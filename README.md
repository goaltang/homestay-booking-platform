# 民宿预订平台 (Homestay Booking Platform)

![License](https://img.shields.io/badge/license-MIT-blue.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.0.2-brightgreen.svg)
![Java](https://img.shields.io/badge/Java-17-orange.svg)
![Vue](https://img.shields.io/badge/Vue-3-42b883.svg)
![Vite](https://img.shields.io/badge/Vite-5%2F6-646CFF.svg)

**[English](README_EN.md)** | 中文

面向房客、房东和平台管理员的民宿预订系统，覆盖房源发布与审核、搜索推荐、下单支付、入住退房、退款争议和收益管理。

这是一个用于学习与面试展示的单人全栈项目，自 2025 年 2 月开始迭代。开发中使用 AI 编码工具辅助实现，项目作者负责架构设计、需求决策、代码审查、关键模块攻关与质量把控。

[安装教程](docs/INSTALL.md) · [完整架构图集](docs/diagrams/README.md) · [功能模块文档](obsidian-vault/02-功能模块/) · [文档首页](obsidian-vault/00-首页.md)

## 目录

- [核心亮点](#核心亮点)
- [系统架构](#系统架构)
- [角色与功能](#角色与功能)
- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [快速开始](#快速开始)
- [测试与构建](#测试与构建)
- [文档入口](#文档入口)
- [安全说明](#安全说明)
- [许可证](#许可证)

## 核心亮点

- **订单与支付协作**：支付宝沙箱页面支付与扫码支付，结合异步回调、状态查询、订单超时处理和退款流程，串联预订到售后的业务状态。
- **RabbitMQ 三个业务场景**：订单超时使用 TTL + 死信队列（DLX）；批量发券记录任务与明细；通知持久化后通过 MQ 消费并推送。各场景分别处理消费重试、定时扫描或 HTTP 读取兜底。
- **有权限边界的 AI 客服 Agent**：只读咨询通过工具白名单编排；退款申请、取消订单和争议申请先起草，再经用户确认提交，并校验订单归属。管理员裁决建议仅生成草稿。
- **动态计价与营销**：周末、节假日、连住和提前预订规则参与计价，支持多级作用域与执行优先级；结合优惠券领取、核销、批量发放和订单价格快照。
- **搜索与个性化推荐**：Elasticsearch 支持关键词、条件与地理位置检索；推荐结合订单、收藏和浏览行为构建画像，提供热门、个性化、位置和相似房源策略。
- **工程化与可观测性**：Flyway 管理迁移，AOP 注解提供限流和审计，Actuator / Micrometer 接入 Prometheus / Grafana；GitHub Actions 执行后端测试与两个前端的类型检查、构建。

## 系统架构

![民宿预订系统架构总览：两个前端应用、Spring Boot 后端、数据设施与外部服务](docs/diagrams/system-overview.png)

房客与房东共用 `homestay-front`，管理员使用独立的 `homestay-admin`。两个前端连接同一个 Spring Boot 后端，后端集成数据存储、缓存、搜索、消息队列、支付宝沙箱与大模型服务。图中端口为本地开发端口。

### 重点详图

| 关注点 | 图解入口 |
|---|---|
| 订单与支付 | [订单生命周期](docs/diagrams/order-lifecycle.png) · [下单事务时序](docs/diagrams/booking-sequence.png) · [支付回调](docs/diagrams/payment-sequence.png) |
| 并发与一致性 | [日期锁与事务边界](docs/diagrams/booking-concurrency.png) · [交易一致性与补偿](docs/diagrams/transaction-consistency.png) |
| RabbitMQ | [订单超时](docs/diagrams/mq-order-timeout.png) · [批量发券](docs/diagrams/mq-coupon-batch.png) · [通知推送](docs/diagrams/mq-notification.png) |
| AI 客服 | [编排与用户确认](docs/diagrams/agent-workflow.png) · [权限与确认边界](docs/diagrams/agent-control-boundaries.png) |
| 定价与推荐 | [统一计价](docs/diagrams/pricing-flow.png) · [定价规则](docs/diagrams/pricing-rules.png) · [搜索与推荐](docs/diagrams/search-recommendation.png) |
| 数据模型 | [预订与支付实体](docs/diagrams/er-booking.png) · [优惠券实体](docs/diagrams/er-coupons.png) |

其余流程、部署和模块协作见[完整图集](docs/diagrams/README.md)。下载项目后，可用浏览器打开 [docs/diagrams/index.html](docs/diagrams/index.html) 查看 HTML 图与代码入口。

**实现边界**：数据库提交与 MQ 发布之间没有 outbox 原子保证；日期锁未覆盖外层事务的完整提交周期；争议仲裁批准路径存在状态前置冲突。具体范围及其他已知限制见[图集核对说明](docs/diagrams/README.md#绘图核对中发现的实现限制)。

## 角色与功能

| 角色 | 主要能力 | 使用入口 |
|---|---|---|
| 房客 | 搜索与地图找房、收藏、预订支付、优惠券、退款申请、评价、聊天与 AI 客服 | 用户端 |
| 房东 | 入驻、发布房源、订单处理、日历库存、入住退房、收益统计、评价回复 | 用户端的房东中心 |
| 管理员 | 房源与身份审核、用户与订单管理、争议处理、定价与营销配置、统计与审计 | 独立管理端 |

游客可浏览公开房源。完整功能说明见[功能模块文档](obsidian-vault/02-功能模块/)，三方协作见[业务泳道图](docs/diagrams/business-swimlane.png)。

## 技术栈

| 层级 | 主要技术 |
|---|---|
| 两个前端 | Vue 3、TypeScript、Vite、Vue Router、Pinia、Element Plus、Axios、ECharts |
| 后端 | Java 17、Spring Boot 3.0.2、Spring Security / JWT、Spring Data JPA、MapStruct |
| 数据与消息 | MySQL 8、Redis / Redisson、Elasticsearch / IK、RabbitMQ、Flyway |
| 外部集成 | 支付宝沙箱、高德地图、OpenAI 兼容 LLM 接口、SMTP、WebSocket / STOMP |
| 工程与监控 | Maven、npm、Docker Compose、GitHub Actions、Actuator、Micrometer、Prometheus、Grafana |

## 项目结构

```text
homestay3/
├── homestay-front/      # 房客 + 房东，Vue 3
├── homestay-admin/      # 管理后台，Vue 3
├── homestay-backend/    # Spring Boot API、迁移与测试
├── docs/               # 安装教程、架构图集
├── obsidian-vault/     # 功能模块、设计分析与工程实践
├── tools/              # 压测、监控与诊断工具
└── docker-compose.yml  # 应用、数据服务与监控配置
```

详细目录和职责见[项目结构总览](docs/项目结构总览.md)。

## 快速开始

以下为本地开发方式。先准备 Java 17、Maven、Node.js 20、MySQL 8、Redis，以及安装 IK 插件的 Elasticsearch；演示三个 MQ 场景时同时启动 RabbitMQ。基础设施准备和完整配置见[安装教程](docs/INSTALL.md)。

> 当前后端启动需要 Elasticsearch 在线。`elasticsearch.enabled=false` 只关闭索引同步并使搜索降级，不能跳过 ES 启动。Docker 部署端口另见[应用部署图](docs/diagrams/deployment-apps.png)。

### 1. 克隆与配置

```bash
git clone https://github.com/goaltang/homestay-booking-platform.git homestay3
cd homestay3/homestay-backend
cp src/main/resources/application.example.properties src/main/resources/application-local.properties
```

编辑 `application-local.properties`：将 `server.port` 改为 `8081`，填写实际 MySQL、Redis 配置和随机 JWT 密钥。支付、地图、邮件与 LLM 的配置按需要填写；AI 客服默认关闭。

### 2. 启动后端

在上一步的后端目录运行，显式加载本地配置：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

后端地址：`http://localhost:8081`；启动时 Flyway 执行数据库迁移。接口文档：`http://localhost:8081/swagger-ui.html`。

### 3. 启动两个前端

分别打开新终端，从项目根目录执行：

```bash
# 房客与房东端
cd homestay-front
npm ci
npm run dev
```

```bash
# 管理端
cd homestay-admin
npm ci
npm run dev
```

用户端：`http://localhost:5173`；管理端：`http://localhost:5174`。两个前端已配置 `/api` 代理到后端。

房客与房东在用户端注册。后端在 `admin` 用户不存在时创建开发管理员 `admin / admin888`，可用于本地演示。

## 测试与构建

[![CI](https://github.com/goaltang/homestay-booking-platform/actions/workflows/ci.yml/badge.svg)](https://github.com/goaltang/homestay-booking-platform/actions/workflows/ci.yml)

以下命令分别从项目根目录开始执行：

```bash
# 后端单元与集成测试
cd homestay-backend
mvn test
```

```bash
# 用户端类型检查与构建
cd homestay-front
npm ci
npm run build
```

```bash
# 管理端类型检查与构建
cd homestay-admin
npm ci
npm run build
```

Spring Boot 集成测试必须使用 `@ActiveProfiles("test")` 与独立 H2 内存数据源，禁止连接真实 MySQL。ES 集成测试使用 Testcontainers 与 IK 镜像，本地没有 Docker 时跳过；镜像构建方式见[安装教程](docs/INSTALL.md)。

CI 配置见 [ci.yml](.github/workflows/ci.yml)；测试与性能结果见下方报告，历史数字不代表当前运行结果。

## 文档入口

| 想了解什么 | 文档 |
|---|---|
| 安装与配置 | [安装教程（含 AI Agent 指引）](docs/INSTALL.md) |
| 架构与业务流程 | [完整架构图集及实现限制](docs/diagrams/README.md) |
| 文档阅读入口 | [文档首页](obsidian-vault/00-首页.md) · [完整目录](<obsidian-vault/00-索引/Homestay 项目索引.md>) |
| 模块职责与目录 | [项目结构总览](docs/项目结构总览.md) · [功能模块文档](obsidian-vault/02-功能模块/) |
| AI 客服设计与验证 | [权限矩阵](obsidian-vault/03-技术设计/AI客服/AI客服Agent-权限与工具边界.md) · [测试报告](obsidian-vault/04-验证与复盘/AI客服Agent-测试报告.md) |
| 性能优化证据 | [首页统计并行化对比](obsidian-vault/04-验证与复盘/性能压测报告-首页统计并行化对比.md) · [管理后台构建优化](obsidian-vault/04-验证与复盘/前端性能优化-管理后台构建体积.md) |
| CI 实践 | [CI 攻坚记录](obsidian-vault/04-验证与复盘/CI-CD攻坚-从零到全绿.md) |
| 前端说明 | [用户端](homestay-front/README.md) · [管理端](homestay-admin/README.md) |

## 安全说明

本地 `application.properties` 与 `application-local.properties` 已被 Git 忽略。仓库提供 [application.example.properties](homestay-backend/src/main/resources/application.example.properties) 配置模板；数据库密码、JWT 密钥、支付密钥与 LLM API Key 应填入本地配置，勿提交真实凭证。

默认管理员与 Compose 中的默认账号仅用于开发演示，对外部署前应修改。

## 许可证

MIT License
