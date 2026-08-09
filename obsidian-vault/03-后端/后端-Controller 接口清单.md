---
title: 后端 Controller 接口清单
date: 2026-04-27
tags:
  - homestay
  - backend
---

# 后端 Controller 接口清单

> [!info] 接口概览
> 后端所有 REST API 接口清单

## 管理员接口 (/api/admin/**)

| Controller | 路径 | 功能 |
|-----------|------|------|
| AdminAuthController | `/api/admin/auth/login` | 管理员登录 |
| AdminUserController | `/api/admin/users` | 用户管理 CRUD |
| AdminHomestayController | `/api/admin/homestays` | 房源管理 CRUD |
| AdminOrderController | `/api/admin/orders` | 订单管理 |
| AdminReviewController | `/api/admin/reviews` | 评价管理 |
| AdminGroupController | `/api/admin/groups` | 房源分组管理 |
| AdminVerificationController | `/api/admin/verifications` | 身份认证审核 |
| AdminAnnouncementController | `/api/admin/announcements` | 公告管理 |
| AdminDisputeController | `/api/admin/disputes` | 争议管理 |
| AdminStatisticsController | `/api/admin/statistics` | 统计数据 |
| AdminSystemConfigController | `/api/admin/system/config` | 系统配置 |
| AdminLoginLogController | `/api/admin/system/login-logs` | 登录日志 |

## 用户接口 (/api/**)

| Controller | 路径 | 功能 |
|-----------|------|------|
| AuthController | `/api/auth/register`<br>`/api/auth/login` | 用户注册/登录 |
| AmenitiesController | `/api/amenities` | 设施列表 |
| AnnouncementController | `/api/announcements` | 公告列表 |
| CheckInController | `/api/checkin` | 入住办理 |
| ChatController | `/api/chat` | 聊天消息 |
| CacheController | `/api/cache/**` | 缓存管理 |
| AlipayCallbackController | `/api/payment/alipay/callback` | 支付宝回调 |

## WebSocket 接口

| 路径 | 功能 |
|------|------|
| `/ws/notifications` | 实时通知推送 |
| `/ws/chat` | 实时聊天 |

## 接口模块详情

### 房源管理接口

```
GET    /api/admin/homestays           # 房源列表
GET    /api/admin/homestays/{id}      # 房源详情
POST   /api/admin/homestays           # 创建房源
PUT    /api/admin/homestays/{id}      # 更新房源
DELETE /api/admin/homestays/{id}      # 删除房源
PATCH  /api/admin/homestays/{id}/status # 状态更新
```

### 订单管理接口

```
GET    /api/admin/orders              # 订单列表
GET    /api/admin/orders/{id}         # 订单详情
PUT    /api/admin/orders/{id}/status  # 状态更新
POST   /api/admin/orders/{id}/refund  # 退款处理
```

### 用户管理接口

```
GET    /api/admin/users               # 用户列表
GET    /api/admin/users/{id}          # 用户详情
PUT    /api/admin/users/{id}/status   # 状态更新
GET    /api/admin/verifications       # 认证审核列表
PUT    /api/admin/verifications/{id}  # 审核操作
```

## 相关笔记

- [[后端-架构总览]]
- [[后端-实体关系图]]
- [[Homestay Admin 管理后台]]
