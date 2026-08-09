---
title: 方案：ES 全文搜索与个性化推荐
date: 2026-04-27
tags:
  - homestay
  - elasticsearch
  - recommendation
---

# 方案：ES 全文搜索与个性化推荐

> [!info] 核心原则
> 先不要上复杂机器学习。做行为埋点 + 规则画像 + ES 搜索排序的版本，够实用，也容易落地。

---

## 现状分析

- **搜索**：当前主要是 JPA 条件过滤 + `like` 查询
  - `homestay-backend/src/main/java/com/homestay3/homestaybackend/service/impl/HomestaySearchServiceImpl.java:33`
- **推荐**：已有热门、相似、个性化接口
  - `homestay-backend/src/main/java/com/homestay3/homestaybackend/service/impl/HomestayRecommendationServiceImpl.java:260`
- **缺口**：未引入 Elasticsearch，无独立用户行为埋点表

---

## 难度判断

| 模块 | 难度 | 说明 |
|------|------|------|
| 全文搜索（ES） | 4/10 ~ 6/10 | 核心难点不是查 ES，而是索引同步、中文分词、筛选排序、数据回退 |
| 个性化推荐 | 6/10 ~ 8/10 | 规则打分不难；真正实时、可解释、效果可评估需补行为数据和画像 |

---

## 新增数据表

### 1. `user_behavior_event`

用户行为埋点表，先记录浏览、搜索、点击、收藏、下单。

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT | 主键 |
| `user_id` | BIGINT | 用户 ID（未登录留空） |
| `session_id` | VARCHAR | 会话 ID |
| `event_type` | VARCHAR | 事件类型：`VIEW` / `SEARCH` / `CLICK` / `FAVORITE` / `BOOKING` / `SHARE` |
| `homestay_id` | BIGINT | 房源 ID（如涉及） |
| `keyword` | VARCHAR | 搜索关键词 |
| `city_code` | VARCHAR | 城市代码 |
| `type` | VARCHAR | 房源类型 |
| `price` | DECIMAL | 房源价格（快照） |
| `extra_json` | JSON | 扩展字段 |
| `created_at` | DATETIME | 发生时间 |

### 2. `user_preference_profile`

用户偏好画像表，按行为聚合更新。

| 字段 | 类型 | 说明 |
|------|------|------|
| `user_id` | BIGINT | 用户 ID（主键） |
| `preferred_city_json` | JSON | 偏好城市及权重 |
| `preferred_type_json` | JSON | 偏好房型及权重 |
| `preferred_amenity_json` | JSON | 偏好设施及权重 |
| `min_price` | DECIMAL | 历史浏览价格下限 |
| `max_price` | DECIMAL | 历史浏览价格上限 |
| `last_active_at` | DATETIME | 最后活跃时间 |

---

## ES 索引设计

索引名：`homestay_index`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | LONG | 房源 ID |
| `title` | TEXT（ik_max_word） | 标题 |
| `description` | TEXT（ik_max_word） | 描述 |
| `addressDetail` | TEXT（ik_max_word） | 详细地址 |
| `provinceCode` | KEYWORD | 省份代码 |
| `cityCode` | KEYWORD | 城市代码 |
| `districtCode` | KEYWORD | 区县代码 |
| `type` | KEYWORD | 房源类型 |
| `amenities` | KEYWORD[] | 设施标签数组 |
| `price` | DOUBLE | 价格 |
| `maxGuests` | INTEGER | 最大入住人数 |
| `rating` | DOUBLE | 评分 |
| `reviewCount` | INTEGER | 评论数 |
| `bookingCount` | INTEGER | 订单数 |
| `favoriteCount` | INTEGER | 收藏数 |
| `location` | GEO_POINT | 经纬度 |
| `status` | KEYWORD | 状态 |
| `createdAt` | DATE | 创建时间 |
| `updatedAt` | DATE | 更新时间 |

---

## 搜索链路

用户搜索时执行以下步骤：

1. **ES 全文检索**：标题、描述、地址、设施
2. **ES 过滤**：城市、价格、人数、房型、设施、可订日期
3. **ES 排序**：相关性、距离、评分、销量、价格
4. **降级回退**：ES 异常时走当前 JPA 搜索

### 排序公式（第一版）

```
score =
    文本相关性 * 0.45
  + 评分/评论质量 * 0.20
  + 预订/收藏热度 * 0.15
  + 用户偏好匹配 * 0.15
  + 新房源扶持 * 0.05
```

---

## 推荐链路

推荐分三类，现有推荐服务保留，只需把用户偏好来源从订单/收藏扩展到行为事件。

### 1. 首页推荐
- 热门 + 高评分 + 新房源扶持

### 2. 详情页推荐
- 同城市、同房型、同价格段、相似设施

### 3. 个性化推荐
- 用户浏览/收藏/下单偏好 + 热门兜底

---

## 实施顺序

| 阶段 | 任务 | 产出 |
|------|------|------|
| 1 | 行为埋点接口和表 | 记录 VIEW / SEARCH / CLICK / FAVORITE / BOOKING |
| 2 | ES 依赖和 `homestay_index` | 房源全量重建接口 |
| 3 | 房源同步机制 | 创建/更新/上下架时同步 ES |
| 4 | 搜索接口改造 | 优先走 ES，失败回退 JPA |
| 5 | 用户画像聚合 | 按行为更新 `user_preference_profile` |
| 6 | 推荐排序改造 | 把用户画像加入打分 |
| 7 | 后续优化 | 搜索建议、热词、同义词、拼音、A/B 测试 |

---

## 工作量估计

| 阶段 | 时间 |
|------|------|
| ES 基础搜索 | 3 ~ 5 天 |
| 行为埋点 + 用户画像 | 3 ~ 5 天 |
| 个性化推荐接入 | 3 ~ 5 天 |
| **稳定可运营版本** | **约 2 周** |

---

## 关键点 checklist

- [x] 行为埋点覆盖 VIEW / SEARCH / CLICK / FAVORITE / BOOKING / SHARE
- [x] ES 索引包含中文分词（预留 IK）和 geo_point
- [x] 房源变更时同步 ES，异常时不阻断主流程
- [x] 搜索接口支持 ES → JPA 降级（`elasticsearch.enabled` 开关）
- [x] 用户画像按小时级聚合（`UserProfileAggregationJob` cron: `0 0 * * * ?`）
- [x] 推荐排序先规则打分，不上复杂机器学习

## 已知问题与修复记录

### 2026-04-27 Code Review 修复

| # | 问题 | 严重度 | 修复方案 |
|---|------|--------|----------|
| 1 | ES 搜索绕过「可订日期」检查 | 严重 | ES 返回结果后，用 `OrderRepository.findConflictingHomestayIds()` 批量排除日期冲突房源 |
| 2 | 重建索引 N+1 性能问题 | 严重 | `rebuildIndex()` 改为批量 SQL 预加载 `avgRatings/reviewCounts/bookingCounts/favoriteCounts` |
| 3 | `isElasticsearchAvailable()` 每次搜索都 ping ES | 中等 | 加 `volatile Boolean` + `lastAvailabilityCheck` 缓存，30 秒内不重复 ping |
| 4 | `BehaviorTrackingController` 取 userId 方式错误 | 中等 | 改为 `SecurityContextHolder` 解析认证 principal |
| 5 | `@Async("taskExecutor")` 可能不存在 | 中等 | `AsyncConfig` 显式提供 `taskExecutor` bean；ES 关闭时用 `NoOpHomestayIndexingService` 兜底 |

### 待办事项

- [ ] 生产环境安装 IK 中文分词器
- [ ] 启动后调用 `POST /api/admin/homestays/rebuild-index` 验证全量同步
- [ ] 线上验证 ES 搜索与 JPA 降级链路

---

## 结论

可以做，且当前系统很适合**渐进式接入**。

- 先做 ES 搜索和行为埋点
- 推荐算法不用一开始做复杂，规则打分就能明显提升体验
- 后续再逐步补充搜索建议、热词、A/B 测试等能力

---

## 相关文档

- [[Homestay Backend 后端服务]]
- [[功能模块-房源管理]]
- [[功能模块-订单管理]]
- [[功能模块-评价管理]]
- [[后端-架构总览]]
