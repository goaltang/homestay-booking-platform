---
title: 后端 Controller 接口清单
date: 2026-08-12
tags:
  - homestay
  - backend
---

# 后端 Controller 接口清单

> [!info] 接口概览
> 后端所有 REST API 接口清单（58 个 Controller、400 个端点，2026-08-12 全量盘点）
> 在线核对：启动后端后访问 `http://localhost:8081/swagger-ui.html`（springdoc 自动生成）

## 管理员接口（/api/admin/**，21 个）

| Controller | 路径 | 端点数 | 功能 |
|-----------|------|-------|------|
| AdminAuthController | `/api/admin/auth` | 3 | 管理员登录 / 登出 / 信息 |
| AdminUserController | `/api/admin/users` | 7 | 用户管理 CRUD、启禁用 |
| AdminHomestayController | `/api/admin/homestays` | 15 | 房源治理、待审核列表、上下架 |
| AdminOrderController | `/api/admin/orders` | 12 | 订单管理、异常统计、退款处理 |
| AdminReviewController | `/api/admin/reviews` | 7 | 评价处理、删除 |
| AdminGroupController | `/api/admin/groups` | 4 | 房源分组、启停 |
| AdminVerificationController | `/api/admin/verifications` | 4 | 身份认证审核（通过/拒绝） |
| AdminAnnouncementController | `/api/admin/announcements` | 7 | 公告管理 CRUD |
| AdminBannerController | `/api/admin/banners` | 7 | Banner 管理 |
| AdminDisputeController | `/api/admin/disputes` | 2 | 争议详情/裁决 |
| AdminDisputeAdvisorController | `/api/admin/disputes/advisor` | 1 | AI 争议裁决建议（`GET /{orderId}/advice`） |
| AdminStatisticsController | `/api/admin` | 8 | 统计总览（含昨日环比）、趋势、导出 |
| AdminSystemConfigController | `/api/admin/system` | 10 | 系统配置（configs、分类查询） |
| AdminLoginLogController | `/api/admin/login-logs` | 1 | 登录日志列表 |
| AdminPromotionController | `/api/admin/promotions` | 22 | 营销活动、批量发券（含限流）、ROI |
| AdminAbTestController | `/api/admin/ab-tests` | 8 | A/B 实验管理 |
| AdminHolidayCalendarController | `/api/admin/holidays` | 6 | 节假日日历、按年生成 |
| AdminPricingRuleController | `/api/admin/pricing-rules` | 5 | 定价规则管理 |
| AdminNotificationController | `/api/admin/notifications` | 4 | 广播通知、广播任务 |
| AdminSearchController | `/api/admin/search` | 1 | ES 索引重建 |
| ViolationController | `/api/admin/violations` | 10 | 违规举报处理 |

## 用户接口（/api/**，37 个）

| Controller | 路径 | 端点数 | 功能 |
|-----------|------|-------|------|
| AuthController | `/api/auth` | 8 | 注册/登录/忘记密码 |
| UserController | `/api/auth` | 3 | 资料维护、改密、dashboard |
| HomestayController | `/api/homestays`（+v1） | 26 | 房源列表/详情/精选 |
| HomestayAuditController | `/api/homestays`（+v1） | 5 | 提交审核、审核记录 |
| HomestayTypeController | `/api/homestay-types`（+v1） | 15 | 房源类型 |
| HomestayImageController | `/api/homestay-images` | 5 | 房源图片上传/管理 |
| HomestayRecommendationController | `/api/recommendations` | 8 | 热门/个性化/相似推荐 |
| AmenitiesController | `/api/amenities`（+v1） | 20 | 设施分类/统计 |
| LocationController | `/api/locations`（+v1） | 4 | 省市区数据 |
| MapSearchController | `/api/map` | 2 | POI 建议、地理编码 |
| AnnouncementController | `/api/announcements` | 2 | 公告列表/详情 |
| OrderController | `/api/orders` | 25 | 下单/详情/取消/状态 |
| CheckInController | `/api/orders` | 13 | 入住凭证、自助入住/退房、押金 |
| PaymentController | `/api/payment` | 3 | 支付创建（限流）/查询/模拟成功 |
| AlipayCallbackController | `/api/payment/alipay` | 1 | 支付宝异步回调 |
| PricingController | `/api/pricing` | 1 | 实时报价 |
| CouponController | `/api/coupons` | 6 | 可领券/我的券/领取 |
| ReviewController | `/api/reviews` | 13 | 评价列表/统计/发布 |
| UserFavoriteController | `/api/favorites` | 9 | 收藏管理 |
| NotificationController | `/api/notifications` | 10 | 通知列表/未读数/已读 |
| ChatController | `/api/chat` | 7 | 会话/消息 |
| BehaviorTrackingController | `/api/tracking` | 3 | 行为埋点 |
| FileController | `/api/files` | 4 | 文件上传/访问 |
| CacheController | `/api/cache` | 3 | 缓存清理/统计 |
| SystemController | `/api/system` | 2 | 时间/健康检查 |
| HomeController | `/api/home` | 2 | 首页统计、Banner |
| SupportAgentController | `/api/support/agent` | 2 | AI 客服对话、写操作确认 |
| HostController | `/api/host` | 12 | 房东信息/统计 |
| EarningController | `/api/host/earnings` | 7 | 收益明细/汇总/趋势 |
| HostCalendarController | `/api/host/calendar` | 5 | 日历库存/锁房/导出 |
| HostPricingRuleController | `/api/host/pricing-rules` | 4 | 房东定价规则 |
| HostPromotionController | `/api/host/promotions` | 8 | 房东营销活动 |
| HomestayGroupController | `/api/host/groups` | 8 | 房东房源分组 |
| OrderAutoStatusController | `/api/host/order-auto-status` | 7 | 订单自动状态/触发/配置 |
| HostDisputeController | `/api/host/disputes` | 2 | 房东争议列表 |
| GuestDisputeController | `/api/guest/disputes` | 1 | 房客发起争议 |
| HomestayResponseAdapter / HomestayWriteRequestAdapter | controller/support/ | 0 | v1 与新版 /api 响应适配（非 REST） |

方法级端点分布：GET 186 / POST 130 / PUT 43 / DELETE 33 / PATCH 8，合计 400。

## WebSocket 接口

| 路径 | 功能 |
|------|------|
| `/ws/notifications` | 实时通知推送 |
| `/ws/chat` | 实时聊天 |

## 关键接口速览

### 认证

```
POST   /api/auth/register          # 用户注册
POST   /api/auth/login             # 用户登录（JWT）
POST   /api/admin/auth/login       # 管理员登录（admin/admin888 默认）
POST   /api/auth/forgot-password   # 忘记密码
```

### 订单全链路

```
POST   /api/orders                 # 创建订单（库存校验）
POST   /api/payment/{orderId}/create   # 发起支付（@RateLimit 限流）
POST   /api/payment/alipay/notify  # 支付宝异步回调
PUT    /api/orders/{id}/status     # 状态流转
PUT    /api/orders/{id}/prepare-checkin  # 准备入住
POST   /api/orders/checkin/self    # 自助入住
POST   /api/orders/{id}/checkout/self   # 自助退房
```

### AI 客服

```
POST   /api/support/agent/chat     # 对话（FAQ + 写操作起草）
POST   /api/support/agent/confirm  # 用户确认后执行写操作
GET    /api/admin/disputes/advisor/{orderId}/advice  # 管理员裁决建议
```

### 营销与运营

```
POST   /api/admin/promotions/batch-tasks   # 创建批量发券任务（MQ 驱动，@RateLimit）
GET    /api/admin/statistics               # 统计总览（含昨日环比）
POST   /api/admin/search/index/rebuild     # ES 索引重建
GET    /api/admin/system/configs           # 系统配置
```

## 相关笔记

- [[后端-架构总览]]
- [[后端-实体关系图]]
- [[Homestay Admin 管理后台]]
