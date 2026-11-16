#!/usr/bin/env python3
"""修复生成数据中的假区县，输出按 id 更新的 SQL。

早期生成的房源把区县写成"<城市名>区"、编码写成"<城市编码前 4 位>0001"，与前端级联选择器、
房东发布表单使用的真实行政区编码不一致，导致按区筛选、按区名搜索都查不到房源。

本脚本：
1. 以后端同一份 china-pca-code.json 为准，在房源所在城市的真实区县（无区县的地级市用乡镇街道）
   中按权重分配一个（市辖"区"权重更高），按房源 id 确定性选择，重复运行结果一致。
2. 用阿里云 DataV 公开边界 GeoJSON，把坐标移到所分配区县的边界内（靠近区县中心），
   保证地图位置与区县一致。没有区县边界时退回城市边界。
3. 只输出 SQL，不连接数据库。每条 UPDATE 带旧编码条件，已修复的行不会被再次改动。

输入为制表符分隔的 id, province_code, city_code, district_code（mysql -N -B 的输出）。
"""

import argparse
import json
import math
import random
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
DIVISIONS = REPO_ROOT / "homestay-backend/src/main/resources/administrative-divisions/china-pca-code.json"
DATAV_URL = "https://geo.datav.aliyun.com/areas_v3/bound/{code}{suffix}.json"
MUNICIPALITIES = {"11", "12", "31", "50"}
URBAN_WEIGHT = 3
# 采样标准差占区县外接框的比例，以及上限（度，约 5 公里）
SIGMA_RATIO = 0.15
SIGMA_MAX = 0.05


def is_fake_district(district_code):
    """真实编码为 6 位（区县）或 9 位（乡镇街道）；生成数据的假编码为 8 位。"""
    return bool(district_code) and len(district_code) == 8 and district_code.isdigit()


def is_functional_zone(code):
    """统计用的开发区、高新区等（第 5-6 位 >= 70），没有行政边界，不参与分配。"""
    return len(code) == 6 and int(code[4:6]) >= 70


def load_cities(path=DIVISIONS):
    """返回 {后端城市编码(6 位): {"province": 2 位, "name": 城市名, "children": [(code, name), ...]}}。"""
    tree = json.loads(Path(path).read_text(encoding="utf-8"))
    cities = {}
    for province in tree:
        for city in province.get("children") or []:
            code = city["code"]
            backend = code + "00" if len(code) == 4 else code
            cities[backend] = {
                "province": province["code"],
                # 与 AdministrativeDivisionService.resolveCityName 一致
                "name": province["name"] if province["code"] in MUNICIPALITIES and city["name"] == "市辖区"
                else city["name"],
                "children": [(c["code"], c["name"]) for c in city.get("children") or []],
            }
    return cities


def weight(name):
    return URBAN_WEIGHT if name.endswith("区") else 1


def choose_district(homestay_id, children):
    children = [c for c in children if not is_functional_zone(c[0])] or children
    rng = random.Random(f"district:{homestay_id}")
    return rng.choices(children, weights=[weight(n) for _, n in children], k=1)[0]


class Geo:
    """按需下载并缓存 DataV 边界。fetch(code, full) 返回 GeoJSON 或 None。"""

    def __init__(self, cache_dir, opener=urllib.request.urlopen, pause=0.1):
        self.cache_dir = Path(cache_dir)
        self.cache_dir.mkdir(parents=True, exist_ok=True)
        self.opener = opener
        self.pause = pause

    def fetch(self, code, full):
        suffix = "_full" if full else ""
        cached = self.cache_dir / f"{code}{suffix}.json"
        if cached.exists():
            text = cached.read_text(encoding="utf-8")
            return json.loads(text) if text else None
        url = DATAV_URL.format(code=code, suffix=suffix)
        data = None
        for attempt in range(3):
            try:
                with self.opener(url, timeout=20) as resp:
                    data = json.loads(resp.read().decode("utf-8"))
                break
            except urllib.error.HTTPError as error:
                if error.code == 404:
                    break  # 该编码没有下级边界（如东莞、中山）
                if attempt == 2:
                    raise
                time.sleep(1 + attempt)
            except json.JSONDecodeError:
                break
            except Exception:
                if attempt == 2:
                    raise
                time.sleep(1 + attempt)
        cached.write_text(json.dumps(data, ensure_ascii=False) if data else "", encoding="utf-8")
        time.sleep(self.pause)
        return data

    def district_shapes(self, city_code, province):
        """返回 {adcode 字符串: feature}，直辖市按省级取。"""
        code = province + "0000" if province in MUNICIPALITIES else city_code
        data = self.fetch(code, full=True)
        if not data:
            return {}
        return {str(f["properties"].get("adcode")): f for f in data.get("features", [])}

    def city_shape(self, city_code, province):
        code = province + "0000" if province in MUNICIPALITIES else city_code
        data = self.fetch(code, full=False)
        features = (data or {}).get("features") or []
        return features[0] if features else None


def short_name(name):
    for suffix in ("自治县", "自治旗", "新区", "区", "县", "市", "旗"):
        if name.endswith(suffix) and len(name) > len(suffix) + 1:
            return name[: -len(suffix)]
    return name


def find_feature(districts, code, name):
    """先按编码，再按名称匹配 DataV 区县（编码调整过的区县，如龙海市改龙海区）。"""
    if code in districts:
        return districts[code]
    wanted = short_name(name)
    for feature in districts.values():
        if short_name(feature["properties"].get("name") or "") == wanted:
            return feature
    return None


def resolve_city(city_code, cities):
    """返回 (城市编码, 城市信息, 固定区县)。

    少数数据把县级市编码存成了城市编码（如大理市 532901），此时归到上级城市，区县即该县级市。
    """
    if city_code in cities:
        return city_code, cities[city_code], None
    parent = city_code[:4] + "00"
    city = cities.get(parent)
    if city:
        for child in city["children"]:
            if child[0] == city_code:
                return parent, city, child
    return city_code, None, None


def polygons(geometry):
    if geometry["type"] == "Polygon":
        return [geometry["coordinates"]]
    if geometry["type"] == "MultiPolygon":
        return geometry["coordinates"]
    return []


def in_ring(x, y, ring):
    inside = False
    j = len(ring) - 1
    for i in range(len(ring)):
        xi, yi = ring[i][0], ring[i][1]
        xj, yj = ring[j][0], ring[j][1]
        if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi) + xi:
            inside = not inside
        j = i
    return inside


def contains(geometry, x, y):
    for poly in polygons(geometry):
        if poly and in_ring(x, y, poly[0]) and not any(in_ring(x, y, hole) for hole in poly[1:]):
            return True
    return False


def bbox(geometry):
    xs, ys = [], []
    for poly in polygons(geometry):
        for x, y in poly[0]:
            xs.append(x)
            ys.append(y)
    return min(xs), min(ys), max(xs), max(ys)


def sample_point(homestay_id, feature):
    """在边界内、靠近中心取点；取不到时用中心点。返回 (lng, lat)。"""
    geometry = feature["geometry"]
    props = feature["properties"]
    center = props.get("centroid") or props.get("center")
    min_x, min_y, max_x, max_y = bbox(geometry)
    if not center:
        center = [(min_x + max_x) / 2, (min_y + max_y) / 2]
    sigma_x = min((max_x - min_x) * SIGMA_RATIO, SIGMA_MAX)
    sigma_y = min((max_y - min_y) * SIGMA_RATIO, SIGMA_MAX)
    rng = random.Random(f"point:{homestay_id}")
    for _ in range(200):
        x = rng.gauss(center[0], sigma_x)
        y = rng.gauss(center[1], sigma_y)
        if contains(geometry, x, y):
            return x, y
    return center[0], center[1]


def sql_str(value):
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def build_updates(rows, cities, geo, log=print):
    """rows: [(id, province_code, city_code, district_code)]。返回 (SQL 语句列表, 统计)。"""
    stats = {"fixed": 0, "skipped_real": 0, "skipped_unknown_city": 0, "coord_fallback_city": 0}
    statements = []
    shapes_cache = {}
    for homestay_id, _province_code, raw_city_code, district_code in rows:
        if not is_fake_district(district_code):
            stats["skipped_real"] += 1
            continue
        city_code, city, fixed = resolve_city(raw_city_code or "", cities)
        if not city or not city["children"]:
            stats["skipped_unknown_city"] += 1
            log(f"跳过 id={homestay_id}：城市 {raw_city_code} 不在行政区数据中")
            continue
        code, name = fixed or choose_district(homestay_id, city["children"])
        if city_code not in shapes_cache:
            shapes_cache[city_code] = (
                geo.district_shapes(city_code, city["province"]),
                geo.city_shape(city_code, city["province"]),
            )
        districts, city_feature = shapes_cache[city_code]
        feature = find_feature(districts, code, name)
        if feature is None:
            feature = city_feature
            stats["coord_fallback_city"] += 1
        city_set = ""
        if city_code != raw_city_code:
            city_set = f", city_code = {sql_str(city_code)}, city_text = {sql_str(city['name'])}"
        coords = ""
        if feature is not None:
            lng, lat = sample_point(homestay_id, feature)
            coords = f", latitude = {lat:.7f}, longitude = {lng:.7f}"
        statements.append(
            f"UPDATE homestays SET district_code = {sql_str(code)}, district_text = {sql_str(name)}{city_set}{coords}"
            f" WHERE id = {int(homestay_id)} AND district_code = {sql_str(district_code)};"
        )
        stats["fixed"] += 1
    return statements, stats


def read_rows(stream):
    rows = []
    for line in stream:
        line = line.rstrip("\r\n")
        if not line:
            continue
        parts = line.split("\t")
        if len(parts) != 4:
            raise ValueError(f"输入行应为 4 列：{line!r}")
        rows.append(tuple(None if p in ("NULL", r"\N") else p for p in parts))
    return rows


def main(argv=None):
    cli = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    cli.add_argument("--input", type=Path, help="mysql -N -B 导出的 TSV（默认读标准输入）")
    cli.add_argument("--output", type=Path, required=True, help="输出 SQL 文件")
    cli.add_argument("--cache-dir", type=Path, required=True, help="DataV 边界缓存目录")
    args = cli.parse_args(argv)

    if args.input:
        with args.input.open(encoding="utf-8") as fh:
            rows = read_rows(fh)
    else:
        rows = read_rows(sys.stdin)
    statements, stats = build_updates(rows, load_cities(), Geo(args.cache_dir), log=lambda m: print(m, file=sys.stderr))
    body = ["-- 由 tools/database/fix_districts.py 生成：把假区县替换为真实区县并移动坐标", "START TRANSACTION;"]
    body += statements
    body.append("COMMIT;")
    args.output.write_text("\n".join(body) + "\n", encoding="utf-8")
    print(json.dumps(stats, ensure_ascii=False), file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
