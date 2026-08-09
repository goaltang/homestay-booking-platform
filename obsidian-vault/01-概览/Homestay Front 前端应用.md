---
title: Homestay Front 前端应用
date: 2026-04-27
tags:
  - homestay
  - overview
---

# Homestay Front 前端应用

> [!info] 前端应用
> 面向 C 端用户的民宿预订前端应用，基于 Vue 3 + TypeScript + Vite 构建，提供房源浏览、地图找房、预订下单、支付等完整流程。

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| **Vue 3** | - | 核心框架（Composition API + `<script setup>`） |
| **TypeScript** | - | 静态类型检查 |
| **Vite** | - | 构建工具 |
| **Vue Router 4** | - | 路由管理 |
| **Pinia** | - | 状态管理 |
| **Element Plus** | - | UI 组件库 |
| **高德地图 JSAPI** | 2.0 | 地图找房、POI 搜索 |
| **Axios** | - | HTTP 请求 |

## 项目结构

```
homestay-front/
├── src/
│   ├── api/              # API 接口封装
│   │   └── homestay/     # 房源相关 API
│   ├── components/       # 组件
│   │   ├── common/       # 通用组件（Header、Footer）
│   │   ├── homestay/     # 房源组件（Card、BookingCard、MapHomestayCard）
│   │   ├── chat/         # 聊天组件
│   │   └── order/        # 订单组件
│   ├── composables/      # 组合式函数
│   │   ├── useMapSearch.ts        # 地图找房核心逻辑
│   │   ├── useMapSearchState.ts   # 搜索状态机
│   │   └── useMapSearchQuerySync.ts # URL 查询同步
│   ├── views/            # 页面视图
│   │   ├── Home.vue                 # 首页
│   │   ├── MapSearch.vue            # 地图找房
│   │   ├── HomestayDetail.vue       # 房源详情
│   │   ├── HomestayListView.vue     # 房源列表
│   │   ├── host/                    # 房东端页面
│   │   ├── user/                    # 用户中心
│   │   └── order/                   # 订单流程
│   ├── stores/           # Pinia 状态管理
│   ├── router/           # 路由配置
│   ├── utils/            # 工具函数
│   │   ├── amapLoader.ts  # 高德地图加载器
│   │   └── mapService.ts  # 地图服务
│   └── types/            # TypeScript 类型定义
├── public/               # 静态资源
└── .env                  # 环境变量配置
```

## 核心页面

| 页面 | 路径 | 说明 |
|------|------|------|
| 首页 | `/` | 推荐房源、搜索入口 |
| 地图找房 | `/map-search` | 交互式地图搜索、周边/地标搜索 |
| 房源列表 | `/homestays` | 列表筛选、排序 |
| 房源详情 | `/homestays/:id` | 详情展示、预订、评价 |
| 订单确认 | `/order/confirm/:id` | 选择日期、填写信息 |
| 订单支付 | `/order/pay/:id` | 支付宝支付 |
| 用户中心 | `/user/profile` | 个人信息、订单、收藏 |
| 房东后台 | `/host/dashboard` | 房源管理、订单管理、收入 |

## 启动方式

```bash
# 1. 进入前端目录
cd homestay-front

# 2. 安装依赖
npm install

# 3. 配置环境变量
cp .env.example .env
# 编辑 .env 填入高德地图 Key 等配置

# 4. 启动开发服务器
npm run dev
```

默认访问: http://localhost:5173

## 相关笔记

- [[Homestay 项目索引]]
- [[功能模块-地图找房]]
- [[Homestay Admin 管理后台]]
- [[Homestay Backend 后端服务]]
- [[开发环境配置]]
