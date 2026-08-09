# homestay3

民宿预订系统（学习 + 面试项目）：C 端用户 + 管理后台 + Spring Boot 后端 + Obsidian 文档仓库。
亮点：RabbitMQ 三场景已上线 —— 订单超时延迟队列(DLX)、批量发券消息驱动、通知推送可靠投递（均带重试队列 + 轮询/降级兜底）。

## 常用命令

### 依赖服务（必须先起）

| 命令 | 说明 |
|------|------|
| `docker-compose up -d` | 启动 ES(9200) + RabbitMQ(5672/15672) |

MySQL 需本机已有（localhost:3306/homestay_db，root/111111，见 application.properties）。

### 一键启动（根目录）

| 命令 | 说明 |
|------|------|
| `npm run dev` | 后端(8081) + 用户端(5173) |
| `npm run dev:admin` | 后端(8081) + 管理后台(5174) |
| `npm run dev:all` | 后端 + 用户端 + 管理后台 全起 |

### 后端（homestay-backend/）

| 命令 | 说明 |
|------|------|
| `mvn spring-boot:run` | 启动，端口 8081 |
| `mvn test` | 跑测试（H2 内存库，见"测试红线"） |
| `mvn package` | 打包 |

### 前端（homestay-front/ 与 homestay-admin/）

| 命令 | 说明 |
|------|------|
| `npm run dev` | 开发（front 5173 / admin 5174） |
| `npm run build` | 构建（vue-tsc 类型检查 + vite build） |
| `npm run test:run` | 单测（vitest，仅 front 配置） |

## 架构概览

| 目录 | 职责 | 技术栈 |
|------|------|--------|
| homestay-front/ | C 端用户：找房/下单/支付/聊天 | Vue 3 + TS + Element Plus + Vite + Pinia |
| homestay-admin/ | 管理后台 | Vue 3 + Element Plus + Vite |
| homestay-backend/ | 后端 API（端口 8081） | Spring Boot 3.0.2 + Java 17 + JPA + Flyway |
| obsidian-vault/ | 项目文档（功能模块/架构/求职） | Obsidian Markdown |
| tools/ | 工具脚本 | — |

依赖服务：MySQL(3306, Flyway 管表结构)、Elasticsearch(9200, 需 IK 插件)、Redis(缓存/会话)、RabbitMQ(homestay-rabbitmq 容器, homestay/homestay123, 管理台 15672)。

后端启动前提：**ES 必须在线**；RabbitMQ 建议在线（MQ 组件条件装配，缺 MQ 时自动降级轮询/同步兜底）。
端口：8080 常被本机 Dify 占用，后端固定用 8081。

## 代码约定

- 后端分层：Controller → Service → Repository；出入参用 DTO，不直接暴露 Entity。
- 表结构变更：Flyway（src/main/resources/db/migration/）加新版本迁移文件，禁止手改表。
- 认证：Spring Security + JWT（jjwt 0.12.3）。
- 前端：组合式 API + `<script setup>`；Element Plus 自动导入（unplugin-auto-import），组件无需手动 import。
- 提交信息：中文，`feat/fix/test/docs` 前缀 + 模块，如 `feat(order): 订单超时延迟队列`。

## 测试红线（强制）

> **没有 `@ActiveProfiles("test")` + 独立数据源的 `@SpringBootTest`，一律视为生产环境炸弹。**

- 所有 `@SpringBootTest` 必须加 `@ActiveProfiles("test")`（application-test.properties 指向 H2 内存库，禁止引用主库连接串）。
- 禁止 `deleteAll()` / `truncate` / `drop` 真实表；写操作靠 `@Transactional` 自动回滚。
- 跑 `mvn test` 前检查：①测试类有 test profile？②数据源是 H2？③有无危险清理操作？
- 历史事故：`ConcurrentBookingTest` 曾连真实 MySQL 执行 `deleteAll()` 清空全表数据。

## 文档规范

Obsidian vault 的 `功能模块-*.md` 有强制模板，完整规范见 `obsidian-vault/02-功能模块/_文档规范模板.md`。核心四条：

1. 只记录**已实现**的功能，不写"建议/计划/后续"。
2. 按用户视角分类（查看/操作/管理），不按技术维度。
3. 多用表格（功能清单/路由/接口/表字段/状态枚举），组件结构用树形缩进。
4. 技术栈在项目级文档写，不重复进功能模块。

## 工具特有文件

- `CLAUDE.md`：graphify 知识图谱工具指令（只影响 Claude Code）。
- `QWEN.md`：Qwen 工具记忆（Obsidian Local REST API、mem0），API Key 一律从 `.env.local` 读取，禁止明文。
- 通用规范以本文件（AGENTS.md）为准。
