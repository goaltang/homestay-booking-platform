import io
import json
import tempfile
import unittest
from pathlib import Path

import fix_districts as fd

SQUARE = {"type": "Polygon", "coordinates": [[[0, 0], [10, 0], [10, 10], [0, 10], [0, 0]]]}


def feature(adcode, geometry, center):
    return {"type": "Feature", "properties": {"adcode": adcode, "center": center}, "geometry": geometry}


class FakeGeo:
    def __init__(self, districts, city):
        self.districts = districts
        self.city = city
        self.calls = 0

    def district_shapes(self, city_code, province):
        self.calls += 1
        return self.districts

    def city_shape(self, city_code, province):
        return self.city


CITIES = {
    "330200": {"province": "33", "name": "宁波市",
               "children": [("330203", "海曙区"), ("330225", "象山县"), ("330271", "宁波经济技术开发区")]},
    "441900": {"province": "44", "name": "东莞市", "children": [("441900003", "东城街道")]},
    "532900": {"province": "53", "name": "大理白族自治州", "children": [("532901", "大理市")]},
}


class FixDistrictsTest(unittest.TestCase):
    def test_detects_fake_code_only(self):
        self.assertTrue(fd.is_fake_district("33020001"))
        self.assertTrue(fd.is_fake_district("53290101"))
        self.assertFalse(fd.is_fake_district("330203"))
        self.assertFalse(fd.is_fake_district("441900003"))
        self.assertFalse(fd.is_fake_district(None))

    def test_choice_is_deterministic_and_within_city(self):
        children = CITIES["330200"]["children"]
        picks = {fd.choose_district(i, children) for i in range(200)}
        self.assertEqual(picks, set(children[:2]))  # 开发区不参与分配
        self.assertEqual(fd.choose_district(42, children), fd.choose_district(42, children))

    def test_urban_districts_weighted_higher(self):
        children = CITIES["330200"]["children"]
        urban = sum(fd.choose_district(i, children)[1] == "海曙区" for i in range(2000))
        self.assertGreater(urban, 1300)

    def test_point_inside_polygon_with_hole(self):
        donut = {"type": "Polygon", "coordinates": [SQUARE["coordinates"][0],
                                                    [[4, 4], [6, 4], [6, 6], [4, 6], [4, 4]]]}
        self.assertTrue(fd.contains(donut, 1, 1))
        self.assertFalse(fd.contains(donut, 5, 5))
        self.assertFalse(fd.contains(donut, 11, 5))
        for i in range(50):
            x, y = fd.sample_point(i, feature(1, donut, [5, 5]))
            self.assertTrue(fd.contains(donut, x, y) or (x, y) == (5, 5))

    def test_builds_guarded_updates_and_falls_back_to_city_shape(self):
        geo = FakeGeo({"330203": feature(330203, SQUARE, [5, 5]), "330225": feature(330225, SQUARE, [5, 5])},
                      feature(441900, SQUARE, [5, 5]))
        rows = [("1", "330000", "330200", "33020001"),
                ("2", "330000", "330200", "330203"),
                ("3", "440000", "441900", "44190001"),
                ("4", "990000", "990100", "99010001"),
                ("5", "330000", "330200", "33020001")]
        logs = []
        statements, stats = fd.build_updates(rows, CITIES, geo, log=logs.append)
        self.assertEqual(stats, {"fixed": 3, "skipped_real": 1, "skipped_unknown_city": 1, "coord_fallback_city": 1})
        self.assertEqual(geo.calls, 2)  # 每个城市只取一次边界
        self.assertIn("WHERE id = 1 AND district_code = '33020001';", statements[0])
        self.assertIn("district_code = '441900003', district_text = '东城街道'", statements[1])
        self.assertIn("latitude = ", statements[1])
        self.assertEqual(len(logs), 1)

    def test_county_stored_as_city_moves_to_parent(self):
        geo = FakeGeo({}, None)
        statements, stats = fd.build_updates([("674", "530000", "532901", "53290101")], CITIES, geo)
        self.assertEqual(stats["fixed"], 1)
        self.assertIn("district_code = '532901', district_text = '大理市', "
                      "city_code = '532900', city_text = '大理白族自治州'", statements[0])
        self.assertNotIn("latitude", statements[0])  # 没有边界时不改坐标

    def test_feature_matched_by_name_when_code_changed(self):
        districts = {"350681": {"properties": {"adcode": 350681, "name": "龙海市"}}}
        self.assertIs(fd.find_feature(districts, "350604", "龙海区"), districts["350681"])
        self.assertIsNone(fd.find_feature(districts, "350605", "长泰区"))

    def test_read_rows_maps_null(self):
        rows = fd.read_rows(io.StringIO("1\t330000\t330200\t33020001\n422\tNULL\tNULL\tNULL\n\n"))
        self.assertEqual(rows[1], ("422", None, None, None))

    def test_geo_caches_missing_codes(self):
        import urllib.error
        calls = []

        def opener(url, timeout):
            calls.append(url)
            raise urllib.error.HTTPError(url, 404, "nf", None, None)

        with tempfile.TemporaryDirectory() as tmp:
            geo = fd.Geo(tmp, opener=opener, pause=0)
            self.assertIsNone(geo.fetch("441900", full=True))
            self.assertIsNone(geo.fetch("441900", full=True))
            self.assertEqual(len(calls), 1)

    def test_loads_real_division_data(self):
        cities = fd.load_cities()
        self.assertIn(("110105", "朝阳区"), cities["110100"]["children"])
        self.assertEqual(cities["110100"]["province"], "11")


if __name__ == "__main__":
    unittest.main()
