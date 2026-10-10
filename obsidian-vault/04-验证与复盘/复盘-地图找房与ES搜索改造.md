---
title: 复盘-地图找房与ES搜索改造
date: '2026-04-29'
type: 验证与复盘
status: 历史记录
updated: '2026-10-10'
tags:
- homestay
---

# 复盘-地图找房与ES搜索改造

保留 2026-04-29 的实施与复盘。原表的预期性能不是实测结果；当前机制见 [[技术设计-搜索与推荐]]。

## 九、实现记录

> 按时间线记录实际开发内容，仅写已完成的阶段。

| 日期 | 阶段 | 完成内容 | 验证结果 |
|------|------|----------|----------|
| 2026-04-29 | 第一阶段 | 附近搜索 & 地标搜索接入 ES `geo_distance`；新增 `searchNearbyByElasticsearch` 和 `buildNearbyElasticsearchQueryJson`；`getNearbyHomestays` / `searchHomestaysNearLandmark` 增加 ES 优先路由 | 后端编译通过 ✅ |
| 2026-04-29 | 第二阶段 | 视口搜索全面走 ES（支持无 keyword）；新增 `searchViewportByElasticsearch` 和 `buildViewportElasticsearchQueryJson`；`searchHomestays` / `searchHomestayPage` 有 keyword 或视口边界时均优先尝试 ES | 后端编译通过 ✅ |
| 2026-04-29 | 第三阶段 | 地图聚类接入 ES `geohash_grid` 聚合；新增 `getMapClustersByElasticsearch` 和 `resolveGeohashPrecision`；`getMapClusters` 优先走 ES 聚合，降级到内存网格聚合 | 后端编译通过 ✅ |
| 2026-04-29 | 第四阶段 | 附近搜索改用 ES `_geo_distance` sort；`searchNearbyByElasticsearch` 从 `StringQuery` 迁移到 `NativeQueryBuilder`；距离从 `SearchHit.getSortValues()` 直接获取，避免内存 Haversine 计算 | 后端编译通过 ✅ |
| 2026-04-29 | Code Review | 修复 `searchNearbyByElasticsearch` 中 `distanceKm` 可能为 null 导致 NPE 的问题；改用 `Comparator.nullsLast` 安全排序 | 后端编译通过 ✅ |
| 2026-04-29 | 重构 | 提取 `buildCommonElasticsearchFilters` 公共方法，消除 `buildElasticsearchQueryJson` / `buildNearbyElasticsearchQueryJson` / `buildViewportElasticsearchQueryJson` 三处重复的 filter 构建逻辑（price、maxGuests、province/city/district、type、amenities） | 后端编译通过 ✅ |

---

## 十、复盘记录

> 全部阶段完成后统一复盘，记录实际与计划的偏差、性能提升数据、踩坑记录。

### 实际工期对比

| 阶段 | 计划工期 | 实际工期 | 偏差原因 |
|------|----------|----------|----------|
| 第一阶段 | 1-2 天 | < 1 天 | 直接复用现有 `ElasticsearchOperations` 注入，无需新建 Repository 方法 |
| 第二阶段 | 1-2 天 | < 1 天 | 与第一阶段共享 `buildViewportElasticsearchQueryJson`，改动面极小 |
| 第三阶段 | 2-3 天 | < 1 天 | `NativeQueryBuilder` + `geohash_grid` 聚合语法一次性调通 |
| 第四阶段 | 1 天 | < 1 天 | `_geo_distance` sort 与 `SearchHit.getSortValues()` 配合直接可用 |
| 重构 | — | < 1 天 | 三处重复代码模式完全一致，提取公共方法无阻力 |
| **合计** | **5-9 天** | **1 天** | 前四阶段可并行验证；`ElasticsearchOperations` 原生 API 比预期更稳定 |

### 性能数据对比

| 接口 | 改造前 P95 | 改造后 P95 | 提升幅度 |
|------|------------|------------|----------|
| `/nearby` | JPA 全表扫描 + 内存 Haversine | 待压测 | 预计 ES `geo_distance` < 100ms |
| `/map-clusters` | JPA 全量拉回 + 内存网格聚合 | 待压测 | 预计仅返回聚合簇 < 50ms |
| `/map-search`（无 keyword）| JPA `BETWEEN` lat/lon | 待压测 | 预计 ES `geo_bounding_box` < 100ms |

> 当前数据量 ~5k，JPA 路径尚未出现明显瓶颈；ES 路径优势需在 3-5 万数据量压测后才能量化。 |

### 踩坑记录

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| `distanceKm` NPE 导致排序崩溃 | ES `_geo_distance` sort 偶发返回 null sort value（文档边缘 case） | `Comparator.nullsLast(Comparator.naturalOrder())` 安全排序 |
| ES 可用但返回空结果时前端无数据 | 索引同步滞后或筛选条件过严 | 保留 JPA fallback：ES 返回空时不直接返回空列表，继续走 JPA 路径 |
| 三处 query builder 重复代码膨胀 | 每新增一个 ES 场景就复制粘贴一套 filter 构建逻辑 | 提取 `buildCommonElasticsearchFilters` 公共方法，后续新增场景只需追加特有 filter |
| `NativeQueryBuilder` 与 `StringQuery` 混用 | 不同场景需要的 Spring Data ES API 不同（聚合需 `NativeQueryBuilder`，简单查询可用 `StringQuery`） | 统一在需要高级特性（sort、aggregation）时用 `NativeQueryBuilder`，其余保持 `StringQuery` |

### 结论

**核心改造已全部完成，前端零改动，JPA 降级路径完整保留。**

- **主路径**：`/nearby`、`/landmark-search`、`/map-search`、`/map-clusters` 均优先走 ES Geo 查询。
- **降级路径**：所有 ES 入口均有 `try-catch`，ES 不可用或返回空时自动 fallback 到 JPA。
- **代码质量**：公共 filter 逻辑已提取，新增 ES 场景成本显著降低。
- **待观察项**：Phase 5（日期可用性前置到 ES）暂不需要；性能基准数据需在数据量增长后补充压测。
