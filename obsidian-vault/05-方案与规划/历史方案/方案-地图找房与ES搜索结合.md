---
title: 地图找房与 ES 搜索结合方案
date: 2026-04-29
tags:
- homestay
- elasticsearch
- geo-search
- architecture
type: 方案
status: 历史记录
updated: '2026-10-10'
---

# 地图找房与 ES 搜索结合方案

> [!note] 阅读范围
> 保留原日期的分析与观察。“当前”“待办”与性能判断不自动代表今天的实现；使用前需核对。现有说明见 [[技术设计-搜索与推荐]]。

> 将地图找房的底层查询从 JPA + 内存计算全面迁移至 Elasticsearch Geo 查询，提升附近搜索、视口搜索、地图聚类三大核心场景的性能。前端接口与交互零改动。

## 一、现状诊断

### 1.1 前端（高完成度，无瓶颈）

| 能力 | 状态 |
|------|------|
| 三种搜索模式（normal / nearby / landmark） | ✅ 完善 |
| 自定义价格 Marker + 防重叠算法 | ✅ 完善 |
| 聚合模式（Cluster）+ 点击下钻 | ✅ 完善 |
| 列表 ↔ 地图 双向联动 + InfoWindow | ✅ 完善 |
| URL 状态同步 + 视口搜索防抖 | ✅ 完善 |

### 1.2 后端（ES Geo 能力严重闲置）

| 场景 | 当前实现 | 问题 |
|------|----------|------|
| 地图边界搜索 | ⚠️ ES `geo_bounding_box` 仅在有 keyword 时启用 | 无关键词的纯视口搜索 fallback 到 JPA |
| 附近搜索（`/nearby`） | ❌ JPA 全量查询 + 内存 Haversine 过滤 | 数据量上升后性能危险 |
| 地标搜索（`/landmark-search`） | ❌ JPA 全量查询 + 内存 Haversine 过滤 | 同上 |
| 地图聚类（`/map-clusters`） | ❌ JPA 全量查询 + 内存网格聚合 | 带宽与内存压力大 |
| 距离排序 | ❌ 内存 Haversine 计算后排序 | 无法利用 ES 原生排序 |
| ES 查询分页 | ❌ `PageRequest.of(0, 10000)` 全量拉回 | 内存分页，存在隐式上限 |
| 日期可用性过滤 | ⚠️ ES 查回后用 JPA 子查询二次过滤 | 大量无效数据回传 |

### 1.3 核心结论

前端地图交互已达 production-ready，瓶颈完全集中在后端查询层。ES 索引 `homestay_index` 已包含 `GeoPoint location` 字段，但 `geo_distance` 查询和地理聚合均未实际投入使用。

---

## 二、改造目标

**一句话：前端接口完全不动，后端把 `/map-search`、`/nearby`、`/landmark-search`、`/map-clusters` 的底层从 JPA 切换到 ES Geo 查询，JPA 仅作为降级兜底。**

### 2.1 量化目标

| 指标 | 当前 | 目标 |
|------|------|------|
| 附近搜索 P95 延迟 | JPA 全表扫描 + 内存计算 | ES `geo_distance` < 100ms |
| 地图聚类返回数据量 | 全量房源列表 | 仅聚合簇（降维 100x+） |
| 视口搜索 ES 覆盖率 | 仅含 keyword 时 | 100% 视口请求 |
| 距离排序计算位置 | 应用内存 | ES 原生排序 |

---

## 三、分阶段实施方案

### 3.1 第一阶段：附近搜索 & 地标搜索接入 ES `geo_distance`（优先级最高）

**背景：** 附近搜索是用户打开地图后的默认高频场景，当前 `searchHomestaysWithinRadius` 走 JPA Specification + 内存 Haversine，是最脆弱的路径。

**改造范围：**
- `HomestayDocumentRepository`：新增组合 geo 查询方法
- `HomestaySearchServiceImpl`：新增 `searchNearbyByElasticsearch`，修改 `getNearbyHomestays` / `searchHomestaysNearLandmark` 路由

**Repository 新增接口：**

```java
@Query("""
{
  "bool": {
    "must": [
      { "match": { "status": "ACTIVE" } },
      { "geo_distance": { "distance": "?5km", "location": { "lat": ?3, "lon": ?4 } } }
    ],
    "filter": [
      { "range": { "price": { "gte": ?0, "lte": ?1 } } },
      { "range": { "maxGuests": { "gte": ?2 } } }
    ]
  }
}
""")
SearchHits<HomestayDocument> searchNearbyWithFilters(
    Double minPrice, Double maxPrice, Integer minGuests,
    Double lat, Double lon, Double distanceKm,
    Pageable pageable);
```

**Service 路由逻辑：**

```java
public List<HomestayDTO> getNearbyHomestays(HomestaySearchRequest request) {
    if (esAvailable) {
        List<HomestayDTO> esResults = searchNearbyByElasticsearch(request);
        if (!esResults.isEmpty()) return esResults;
    }
    return searchHomestaysWithinRadius(request, limit); // JPA 降级
}
```

**关键细节：**
- 距离排序由 ES `_geo_distance` sort 完成，不再内存计算
- amenities / 日期等过滤条件需同步叠加到 ES query
- 返回 pipeline 保持：ES → JPA 补全实体 → 日期冲突过滤 → DTO 组装

**前端影响：** 零改动。

---

### 3.2 第二阶段：视口搜索全面走 ES（支持无 keyword）

**背景：** `searchByElasticsearch()` 第 727 行硬门槛 `if (!StringUtils.hasText(keyword)) return emptyList;` 导致纯地图拖动（无关键词）完全不走路 ES。

**改造范围：**
- `HomestaySearchServiceImpl`：拆分 `searchByElasticsearch` 为两个入口

**拆分设计：**

| 入口 | 触发条件 | ES Query 类型 |
|------|----------|---------------|
| `searchByElasticsearchWithKeyword` | `keyword` 非空 | `function_score` + `multi_match` |
| `searchByElasticsearchWithoutKeyword` | 仅地图边界/筛选 | `bool` + `filter` + `geo_bounding_box` |

**无 keyword 时的 query 结构：**

```json
{
  "bool": {
    "must": [{ "match": { "status": "ACTIVE" } }],
    "filter": [
      { "range": { "price": { "gte": 100, "lte": 800 } } },
      { "range": { "maxGuests": { "gte": 2 } } },
      { "geo_bounding_box": { "location": { "top_left": {...}, "bottom_right": {...} } } }
    ]
  }
}
```

**路由策略调整：**

```java
boolean shouldTryEs = StringUtils.hasText(request.getKeyword()) || hasViewportBounds(request);
```

---

### 3.3 第三阶段：地图聚类接入 ES `geohash_grid` 聚合

**背景：** `getMapClusters()` 先 `searchHomestays(request)` 拉回全部符合条件的房源，再在 Java 内存按 `gridSize` 分桶。当房源数上万时，带宽和内存压力极大。

**改造范围：**
- `HomestaySearchServiceImpl`：新增 `getMapClustersByElasticsearch`
- 使用 `ElasticsearchOperations` 执行原生聚合查询

**ES 聚合 query：**

```json
{
  "query": { "bool": { "filter": [...] } },
  "aggs": {
    "clusters": {
      "geohash_grid": {
        "field": "location",
        "precision": 5
      },
      "aggs": {
        "avg_lat": { "avg": { "field": "location.lat" } },
        "avg_lon": { "avg": { "field": "location.lon" } }
      }
    }
  },
  "size": 0
}
```

**zoom → precision 映射：**

| zoom | geohash precision | 说明 |
|------|-------------------|------|
| 3-5  | 2 | 国家级/大区级 |
| 6-8  | 3 | 省级 |
| 9-11 | 4 | 城市级 |
| 12-14| 5 | 商圈级 |
| 15+  | 6 或禁用聚合 | 街道级，直接展示 Marker |

**优势：**
- ES 仅返回聚合结果（通常 < 100 个簇），不返回原始房源
- 计算下推到 ES 集群，应用服务器内存压力归零

---

### 3.4 第四阶段：距离排序 & 分页优化

**背景：**
1. ES 查询硬编码 `PageRequest.of(0, 10000)`，全量拉回后内存分页
2. 距离排序在 `applySearchSorting()` 中用 Haversine 内存计算

**改造点：**

| 优化项 | 当前 | 改造后 |
|--------|------|--------|
| 距离排序 | Java 内存 Haversine | ES `_geo_distance` sort，结果直接映射 `distanceKm` |
| 分页上限 | 10000 条硬上限 | 视场景调整为 500~2000，或改用 `search_after` |

**ES `_geo_distance` sort 示例：**

```json
{
  "sort": [
    {
      "_geo_distance": {
        "location": { "lat": 31.23, "lon": 121.47 },
        "order": "asc",
        "unit": "km"
      }
    }
  ]
}
```

---

### 3.5 第五阶段：日期可用性前置到 ES（进阶，视业务量）

**背景：** ES 查回一批 ID 后，再用 `orderRepository.findConflictingHomestayIds(...)` 做二次过滤。若 ES 返回 5000 条其中 4900 条冲突，则浪费严重。

**方案（选做）：**
- 在 `HomestayDocument` 中增加 `bookedDates` 字段（`List<Date>`），或预先计算未来 180 天每日可订状态
- 查询时将日期冲突过滤前置到 ES `bool.must_not`

**门槛：** 需要维护每日库存同步到 ES，复杂度较高。建议房源量 > 5 万或并发高时再考虑。

---

## 四、接口路由总览（改造后）

| 前端接口 | 改造前底层 | 改造后主路径 | 降级路径 |
|----------|------------|--------------|----------|
| `POST /map-search` | JPA Specification | ES `geo_bounding_box` + filters | JPA `BETWEEN` lat/lon |
| `POST /nearby` | JPA + Haversine | ES `geo_distance` + `_geo_distance` sort | JPA + Haversine |
| `POST /landmark-search` | JPA + Haversine | ES `geo_distance` + `_geo_distance` sort | JPA + Haversine |
| `POST /map-clusters` | JPA + 内存聚合 | ES `geohash_grid` agg | JPA + 内存聚合 |
| `POST /search`（keyword） | ES function_score | ES function_score（不变） | JPA LIKE |

---

## 五、关键文件清单

### 5.1 后端核心文件

| 文件 | 改动内容 |
|------|----------|
| `HomestayDocumentRepository.java` | 新增 `searchNearbyWithFilters`、`searchViewportWithoutKeyword`、`searchClustersAggregation` 等方法 |
| `HomestaySearchServiceImpl.java` | 拆分 ES 查询入口、修改路由逻辑、新增 geo_distance / geohash_grid 查询实现 |
| `HomestayDocument.java` | 视第五阶段决定是否增加 `bookedDates` 字段 |

### 5.2 前端文件

| 文件 | 改动内容 |
|------|----------|
| `api/homestay/search.ts` | 零改动，请求参数与响应格式不变 |
| `composables/useMapSearch.ts` | 零改动 |
| `views/MapSearch.vue` | 零改动 |

---

## 六、实施计划

| 阶段 | 内容 | 预估工期 | 前置依赖 |
|------|------|----------|----------|
| 第一阶段 | 附近/地标搜索接入 ES `geo_distance` | 1-2 天 | 无 |
| 第二阶段 | 视口搜索支持无 keyword 走 ES | 1-2 天 | 第一阶段完成 |
| 第三阶段 | 地图聚类接入 ES `geohash_grid` 聚合 | 2-3 天 | 第二阶段完成 |
| 第四阶段 | 距离排序 + 分页微调 | 1 天 | 第三阶段完成 |
| 第五阶段 | 日期可用性前置到 ES（进阶） | 3-5 天 | 业务量评估后 |

**推荐启动顺序：** 先 1 后 2。附近搜索是独立接口，和现有 `searchByElasticsearch` 的 keyword 逻辑解耦，改动面最小，风险最低，用户感知最明显。

---

## 七、风险与应对

| 风险 | 影响 | 应对措施 |
|------|------|----------|
| ES 不可用导致地图搜索异常 | 高 | 保留 JPA 降级路径，所有 ES 查询增加 `try-catch` + fallback |
| geo_distance 与 Haversine 距离精度差异 | 中 | 统一使用 ES 返回的 `_geo_distance` 作为 `distanceKm` 唯一来源 |
| 聚合精度（geohash）与前端 gridSize 不一致 | 低 | 前端聚合展示不依赖精确 gridSize，仅依赖 count + 中心坐标 |
| 日期过滤后置导致结果偏少 | 中 | 第四阶段前保持现有 pipeline；第五阶段再前置 |

---

## 八、验证清单

### 8.1 接口层验证

- [ ] `/nearby` 有 ES 时走 ES，ES 关闭时走 JPA 且结果一致
- [ ] `/landmark-search` 有 ES 时走 ES，ES 关闭时走 JPA 且结果一致
- [ ] `/map-search` 无 keyword 时走 ES `geo_bounding_box`
- [ ] `/map-clusters` 返回簇数量远小于房源数量
- [ ] 所有接口的 `distanceKm` 字段与前端展示一致

### 8.2 性能验证

- [ ] 附近搜索 P95 < 100ms（ES 路径）
- [ ] 地图聚类接口响应 < 50ms（ES 路径）
- [ ] 视口搜索在 10w 房源数据下仍 < 200ms

### 8.3 前端验证

- [ ] 地图 Marker 加载与改造前一致
- [ ] 列表 ↔ 地图联动无异常
- [ ] 聚合模式点击下钻正常
- [ ] URL 状态同步正常

---

## 实施结果入口

实际时间线与观察见 [[复盘-地图找房与ES搜索改造]]；当前机制见 [[技术设计-搜索与推荐]]。本页保留原诊断与分阶段设计。
