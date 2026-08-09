# 民宿预订平台 (Homestay Booking Platform)

![License](https://img.shields.io/badge/license-MIT-blue.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.0.2-brightgreen.svg)
![Java](https://img.shields.io/badge/Java-17-orange.svg)
![Vue](https://img.shields.io/badge/Vue-3-42b883.svg)
![Vite](https://img.shields.io/badge/Vite-5%2F6-646CFF.svg)

**[English](README_EN.md)** | 中文

民宿预订平台是一个连接房客、房东和平台管理员的综合业务系统，覆盖房源发布、平台审核、在线搜索、下单支付、入住退房、评价反馈、收益统计和后台监管等完整流程。

> 25 个业务模块 · 10W+ 行代码 · 223 次 commit · 持续迭代 16 个月 · 50+ 篇项目文档

## 项目背景

Homestay 是一个**单人独立交付**的全栈项目，覆盖 25 个业务模块、10W+ 行代码，后端 Spring Boot 3 + 前端 Vue 3 + MySQL / Redis / Elasticsearch / 支付宝完整链路。

项目从 2025 年 2 月持续迭代至今，我借助 AI 编码工具（Cursor / Claude Code / Kimi Code CLI 等）完成 90% 的编码工作，自己负责**架构设计、需求决策、代码审查、关键模块攻关与质量把控**。

这种工作流让我一个人就能 cover 三端（用户端 / 房东端 / 管理员端）的全部交付，也是 2026 年 AI 时代全栈工程师的一个真实缩影。

> 工具链迭代：Cursor Pro → Claude Code CLI → Kimi Code CLI

## 目录

- [项目亮点](#项目亮点)
- [技术栈](#技术栈)
- [系统架构](#系统架构)
- [系统角色](#系统角色)
- [功能概览](#功能概览)
- [核心业务流程](#核心业务流程)
- [项目结构](#项目结构)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [测试与构建](#测试与构建)
- [文档索引](#文档索引)
- [安全说明](#安全说明)
- [许可证](#许可证)

## 项目亮点

- **三端一体**：用户端、房东端、管理员端共用后端能力，分别覆盖消费、经营和平台治理场景。
- **Elasticsearch 房源搜索**：支持全文检索、条件筛选、地理坐标索引与搜索、相似房源推荐，搜索结果可个性化排序。
- **智能个性化推荐引擎**：基于用户历史订单、收藏行为、浏览轨迹构建用户画像，实现热门推荐、个性化推荐、基于位置的推荐和相似房源推荐四种策略，支持三级降级兜底与结果多样化。
- **动态定价引擎**：支持周末溢价、节假日调价、连住折扣、提前预订优惠等多维度定价规则，作用域覆盖全局/城市/房东/房源组/单个房源，支持调休补班识别与订单价格快照锁定。
- **用户行为追踪与画像**：异步埋点采集搜索、浏览、点击、收藏、预订、分享等行为事件，定时聚合用户画像（价格偏好、位置偏好、房型偏好、设施偏好），反哺推荐引擎形成闭环。
- **优惠券营销系统**：支持优惠券模板管理、用户领取（悲观锁防超领）、订单核销、过期清理、批量发放与 ROI 分析。
- **订单自动化状态机**：基于定时任务实现订单自动入住、自动退房、超时取消（待确认/待支付/支付中三级场景），支持历史订单状态批量修复。
- **房源智能特征分析**：从房源类型、价格竞争力、设施组合、位置优势、预订活跃度、周末流行度、用户评价、入住便利性 8 个维度自动生成房源特色标签，并根据用户搜索条件动态提升匹配标签优先级。
- **Redis 分布式锁**：基于 Lua 脚本实现原子解锁，防止误删其他线程的锁，Redis 不可用时自动降级为无锁模式。
- **价格竞争力分析**：同区域/同城市/同类型多级降级比价，结合季节性因子输出竞争力等级与价格建议。
- **地图找房体验**：集成高德地图，支持位置搜索、周边检索、距离计算和地图展示。
- **支付集成**：接入支付宝沙箱支付（页面跳转支付 + 扫码支付），支持订单支付、异步回调、状态查询和退款。
- **工程化后端**：统一响应、全局异常、权限注解、DTO 映射、缓存、数据库迁移和审计日志。

## 技术栈

| 模块 | 技术 |
|---|---|
| 用户端 | Vue 3、TypeScript、Vite、Vue Router、Pinia、Element Plus、Axios、ECharts、高德地图、SockJS、STOMP |
| 管理端 | Vue 3、TypeScript、Vite、Vue Router、Pinia、Element Plus、Axios、ECharts |
| 后端 | Java 17、Spring Boot 3.0.2、Spring Web、Spring Security、Spring Data JPA、Spring Validation |
| 数据与缓存 | MySQL 8.0、Redis、Redisson、Flyway、Elasticsearch |
| 通信与集成 | JWT、WebSocket（STOMP）、RabbitMQ（AMQP）、支付宝 SDK、SMTP 邮件 |
| 工程工具 | Maven、npm、MapStruct、Lombok、Docker Compose |

## 系统架构

```mermaid
graph TB
    subgraph 客户端
        F1[用户端<br/>Vue 3 + TypeScript]
        F2[房东端<br/>Vue 3 + TypeScript]
        F3[管理端<br/>Vue 3 + TypeScript]
    end

    subgraph 后端服务 [Spring Boot 3 + Java 17]
        S1[统一认证<br/>Spring Security + JWT]
        S2[核心业务<br/>房源 / 订单 / 评价 / 支付]
        S3[定价引擎<br/>动态报价 / 节假日 / 连住]
        S4[ES 搜索 +<br/>个性化推荐]
        S5[营销促销<br/>优惠券 / 邀请 / ROI]
        S6[通知中心<br/>WebSocket 实时推送]
    end

    subgraph 数据与缓存
        D1[(MySQL 8<br/>Flyway V49)]
        D2[(Redis<br/>分布式锁 + 缓存)]
        D3[(Elasticsearch<br/>IK + Geo 查询)]
    end

    subgraph 外部服务
        E1[支付宝]
        E2[高德地图]
        E3[SMTP 邮件]
    end

    F1 --> S1
    F2 --> S1
    F3 --> S1
    F1 --> S2
    F2 --> S2
    F3 --> S2
    F1 --> S3
    F2 --> S3
    F1 --> S4
    F1 --> S5
    F2 --> S5
    F1 --> S6
    F2 --> S6

    S2 --> D1
    S3 --> D1
    S4 --> D3
    S4 --> D2
    S5 --> D1
    S6 --> D2
    S2 --> D2
    S2 --> E1
    S2 --> E2
    S1 --> E3
```

## 系统角色

| 角色 | 说明 |
|---|---|
| 游客 | 可浏览首页、搜索房源、查看公开房源详情 |
| 房客 | 可收藏房源、提交订单、支付、退款、评价和发送消息 |
| 房东 | 可入驻平台、发布房源、管理订单、处理入住退房、查看收益统计 |
| 管理员 | 可审核房源、管理用户和订单、处理举报争议、查看统计数据和配置平台规则 |

## 功能概览

### 用户端

| 模块 | 主要能力 |
|---|---|
| 账号认证 | 注册、登录、JWT 鉴权、密码重置、个人资料维护 |
| 首页推荐 | 热门民宿、个性化推荐、基于位置的推荐 |
| 房源搜索 | Elasticsearch 关键词搜索、条件筛选、排序分页、URL 参数持久化、相似房源推荐 |
| 地图找房 | 高德地图展示、周边搜索、坐标定位、距离计算 |
| 房源详情 | 图片、设施、智能特色标签、价格、位置、房东信息、评价列表 |
| 在线预订 | 日期选择、动态定价实时计价、订单预览、库存校验 |
| 在线支付 | 支付宝页面跳转或扫码支付、支付状态查询、支付成功回跳 |
| 订单管理 | 订单列表、订单详情、取消订单、退款申请、状态跟踪 |
| 收藏评价 | 收藏/取消收藏、完成订单后评价、查看我的评价 |
| 消息通知 | 房客与房东即时通讯、未读提醒、系统通知（WebSocket 实时推送） |

### 房东端

| 模块 | 主要能力 |
|---|---|
| 房东入驻 | 入驻资料填写、身份认证、房东资料维护 |
| 房东控制台 | 房源、订单、收入、评价和最近订单概览 |
| 房源管理 | 创建、编辑、草稿保存、提交审核、上下架、删除、分组管理 |
| 房源发布 | 基本信息、位置、设施、描述、图片等分步录入 |
| 订单处理 | 查看订单、确认订单、拒绝订单、退款审核、争议处理 |
| 入住退房 | 生成入住凭证、自助入住码、办理入住、退房结算、押金和额外费用 |
| 收益管理 | 总收益、本月收益、未结算收益、日/月趋势统计、数据导出 |
| 评价管理 | 评分分布、评价列表、房东回复、未回复提醒 |
| 消息通知 | 会话列表、聊天记录、订单和审核通知 |

### 管理员端

| 模块 | 主要能力 |
|---|---|
| 审核工作台 | 待审核房源、批量审核、审核历史、审核统计 |
| 房源治理 | 房源列表、强制下架、违规记录、房源类型和设施管理 |
| 用户管理 | 用户列表、启用/禁用、身份认证审核 |
| 订单管理 | 多条件筛选、退款审核、争议解决 |
| 违规管理 | 举报列表、处理/忽略举报、违规扫描、重复举报统计 |
| 数据统计 | 订单、收入、用户、房源概览和趋势分析 |
| 定价规则 | 全局/城市/房东/房源多级定价规则配置与优先级管理 |
| 优惠券管理 | 模板创建、批量发放、使用统计与 ROI 分析 |
| 系统配置 | 平台配置、政策配置、费用配置 |
| 公告管理 | 发布系统通知和活动公告 |
| 日志审计 | 管理员操作日志、登录日志 |

### 后端能力

| 能力 | 说明 |
|---|---|
| 统一响应 | 使用 `ApiResponse<T>` 统一返回 `success`、`code`、`message`、`data` 和 `timestamp` |
| 认证授权 | 使用 Spring Security + JWT 实现无状态认证，并按角色控制接口访问 |
| 数据访问 | 使用 Spring Data JPA、Repository 和 Specification 支持复杂查询 |
| 数据迁移 | 使用 Flyway 管理数据库结构演进（V1 ~ V49） |
| 缓存加速 | 使用 Redis 缓存热点数据和推荐数据，Spring Cache 管理推荐缓存 |
| 分布式锁 | 基于 Redis + Lua 脚本实现分布式锁，支持故障降级 |
| 实时通信 | 使用 WebSocket（STOMP 协议）支持聊天消息和通知的实时推送 |
| 搜索服务 | 基于 Elasticsearch 构建房源搜索引擎，支持增量同步与全量重建 |
| 推荐服务 | 多策略推荐引擎 + 用户画像服务 + 行为追踪，支持缓存与降级 |
| 定价引擎 | 多维度动态定价规则引擎，支持日期级与订单级调价，规则优先级与叠加控制 |
| 支付集成 | 接入支付宝沙箱支付，支持页面跳转支付、扫码支付、异步通知、订单查询和退款 |
| 异常处理 | 使用 `@RestControllerAdvice` 统一处理业务异常和系统异常 |
| 对象映射 | 使用 MapStruct 完成 Entity、DTO、Request、Response 转换 |
| 审计记录 | 异步记录管理员操作、登录行为和关键业务状态变化 |
| 定时任务 | 订单状态自动流转、超时处理、优惠券清理、用户画像聚合 |

## 核心业务流程

### 房源发布与审核

```text
房东创建房源草稿
  -> 补充位置、设施、描述和图片
  -> 提交审核
  -> 管理员审核
  -> 审核通过后上线 / 审核拒绝后退回修改
```

### 订单生命周期

```text
房客提交订单
  -> 待支付
  -> 已支付
  -> 房东确认
  -> 准备入住
  -> 已入住（定时任务自动流转）
  -> 已退房（定时任务自动流转）
  -> 已完成
  -> 房客评价
```

### 退款与争议

```text
房客申请退款
  -> 房东审核
  -> 同意退款 -> 退款中 -> 退款完成
  -> 拒绝退款 -> 用户发起争议 -> 管理员介入处理
```

### 收益结算

```text
订单完成
  -> 生成房东收益
  -> 进入可结算金额
  -> 房东查看收益统计与趋势
```

## 项目结构

```text
homestay3/
├── homestay-front/          # 用户端 + 房东端，Vue 3 + Vite
├── homestay-admin/          # 管理员端，Vue 3 + Vite
├── homestay-backend/        # 后端 API，Spring Boot
├── docs/                    # 项目说明文档
│   └── INSTALL.md           # 安装教程（含 AI Agent 安装指引）
├── tools/                   # 本地工具脚本或辅助工具
├── docker-compose.yml       # Docker Compose 配置（含 Elasticsearch）
├── README.md                # 项目总览（中文）
├── README_EN.md             # 项目总览（英文）
└── .gitignore               # Git 忽略规则
```

后端主要分层：

```text
com.homestay3.homestaybackend
├── config/                  # 安全、缓存、跨域、WebSocket、支付等配置
├── controller/              # REST API 控制器
├── service/                 # 核心业务逻辑
│   ├── search/              # 搜索与推荐相关服务
│   └── gateway/             # 支付网关
├── repository/              # JPA 数据访问层
├── entity/                  # 数据库实体
├── dto/                     # 数据传输对象
├── mapper/                  # MapStruct 映射
├── model/                   # 枚举和常量
├── exception/               # 全局异常处理
├── security/                # JWT 与认证授权
├── util/                    # 通用工具类
└── job/                     # 定时任务
```

## 快速开始

> 完整的安装教程（含 AI Agent 自动安装指引）见 [docs/INSTALL.md](docs/INSTALL.md)。

### 环境要求

| 环境 | 推荐版本 | 必需 |
|---|---|---|
| JDK | 17+ | ✅ |
| Maven | 3.6+ | ✅ |
| MySQL | 8.0+ | ✅ |
| Redis | 6.0+ | ✅ |
| Elasticsearch | 8.5+ | ❌（可选，搜索功能依赖） |
| RabbitMQ | 3.13+（management） | ❌（可选，订单超时延迟队列依赖） |
| Node.js | 18+ | ✅ |
| npm | 9+ | ✅ |
| Docker + Docker Compose | 最新稳定版 | ❌（仅用于启动 ES / RabbitMQ） |

### 1. 克隆项目

```bash
git clone https://github.com/goaltang/homestay3.git
cd homestay3
```

### 2. 启动基础设施

**MySQL** — 创建数据库：

```bash
mysql -u root -p -e "CREATE DATABASE homestay_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

**Redis** — 确保 Redis 在 `localhost:6379` 运行：

```bash
redis-server --daemonize yes
```

**Elasticsearch（可选）** — 通过 Docker Compose 启动：

```bash
docker-compose up -d elasticsearch
```

> 如果不需要搜索功能，可在 `application.properties` 中设置 `elasticsearch.enabled=false`，后端会自动降级为 JPA 数据库搜索。

**RabbitMQ（可选，订单超时延迟队列）** — 通过 Docker Compose 启动：

```bash
docker-compose up -d rabbitmq
```

> 管理台地址：`http://localhost:15672`（默认账号 `homestay / homestay123`，可在 compose 中修改）。订单超时采用「RabbitMQ 延迟队列为主 + 定时轮询兜底」双保险：MQ 不可用时，轮询任务仍会取消超时订单，因此不启动 RabbitMQ 也不影响系统正确性。

### 3. 配置后端

复制配置模板并修改：

```bash
cd homestay-backend
cp src/main/resources/application.example.properties src/main/resources/application-local.properties
```

编辑 `application-local.properties`，填入本地的 MySQL 密码、Redis 密码、JWT 密钥等。

> 也可以直接修改 `application.properties`，但请注意不要将敏感信息提交到仓库。

### 4. 启动后端

```bash
cd homestay-backend
mvn clean compile
mvn spring-boot:run
```

后端默认运行在 `http://localhost:8080`。Flyway 会自动执行全部数据库迁移（V1 ~ V49，共 41 个脚本），无需手动建表。

### 5. 启动用户端和房东端

```bash
cd homestay-front
cp .env.example .env.local   # 可选，配置高德地图 Key
npm install
npm run dev
```

访问 `http://localhost:5173`。Vite 已配置代理，`/api` 请求自动转发到后端 `http://127.0.0.1:8080`。

### 6. 启动管理员端

```bash
cd homestay-admin
npm install
npm run dev
```

访问 `http://localhost:5174`。Vite 已配置代理，`/api` 请求自动转发到后端。

### 首次使用

项目没有预置管理员账号。首次启动后端时，`DataInitializer` 会自动初始化默认设施数据。

- **房客 / 房东**：在用户端 (`localhost:5173`) 注册账号即可使用。
- **管理员**：注册后，在数据库中将用户的 `role` 字段改为 `ADMIN`：

```sql
UPDATE users SET role = 'ADMIN' WHERE email = 'your-email@example.com';
```

## 配置说明

后端配置文件位于：

```text
homestay-backend/src/main/resources/application.properties
```

本地运行需要关注这些配置：

| 配置 | 说明 |
|---|---|
| `spring.datasource.*` | MySQL 连接地址、用户名和密码 |
| `spring.data.redis.*` | Redis 地址、端口、密码和数据库编号 |
| `spring.elasticsearch.*` | Elasticsearch 连接地址（可选） |
| `elasticsearch.enabled` | 设为 `false` 可跳过 ES，降级为 JPA 搜索 |
| `spring.rabbitmq.*` | RabbitMQ 连接地址、端口、账号（可选，订单超时延迟队列依赖） |
| `order.timeout.mq-enabled` | 订单超时 MQ 消费开关，设为 `false` 时仅靠轮询兜底 |
| `order.timeout.*` | 订单超时时长（pending / confirmed / payment-pending 小时数、预警提前分钟数） |
| `jwt.secret` | JWT 签名密钥 |
| `spring.mail.*` | 邮件服务配置 |
| `payment.alipay.*` | 支付宝沙箱应用、公钥、私钥、网关和回调地址 |
| `file.upload-dir` | 上传文件保存目录 |

前端环境变量：

| 文件 | 说明 |
|---|---|
| `homestay-front/.env.example` | 用户端环境变量模板（高德地图 Key 等） |

## 测试与构建

### 后端

```bash
cd homestay-backend
mvn test
mvn clean package
```

测试构成：
- 单元测试：`src/test/java/.../mq/`（MQ 消费者：订单超时/批量发券/通知推送）、`service/impl/`（订单/优惠券等）
- API 自动化测试：`src/test/java/.../api/`（AuthApiTest / OrderApiTest / CouponApiTest / NotificationApiTest，验证认证、下单、发券、通知全链路，H2 内存库 + MQ 降级路径）
- 集成测试：`src/test/java/.../integration/`（历史遗留）

> ⚠️ 测试安全红线：所有测试必须走 `application-test.properties`（H2 内存库），禁止连接真实 MySQL（历史曾发生测试清空生产数据事故）。

### 用户端

```bash
cd homestay-front
npm run build
```

### 管理员端

```bash
cd homestay-admin
npm run build
```

## 文档索引

| 文档 | 说明 |
|---|---|
| [安装教程](docs/INSTALL.md) | 详细安装指南，含 AI Agent 自动安装指引 |
| [项目结构总览](docs/项目结构总览.md) | 项目目录和模块职责 |
| [项目技术栈说明](docs/项目技术栈说明.md) | 技术选型和依赖说明 |
| [开发环境配置指南](docs/开发环境配置指南.md) | 本地开发环境准备 |
| [homestay-admin 详细结构](docs/homestay-admin%20详细结构.md) | 管理端目录结构说明 |
| [用户端说明](homestay-front/README.md) | 用户端和房东端前端说明 |
| [管理端说明](homestay-admin/README.md) | 管理端前端说明 |

## 安全说明

本仓库 `application.properties` 中的数据库密码、JWT 密钥、支付宝私钥等敏感信息均为占位符，
请复制为 `application-local.properties` 并填入本地真实值（已在 `.gitignore` 中排除）。

## 许可证

MIT License
