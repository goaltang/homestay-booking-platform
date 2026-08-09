# homestay3

民宿预订系统（学习 + 面试项目）：C 端用户 + 管理后台 + Spring Boot 后端 + Obsidian 文档仓库。
亮点：RabbitMQ 三场景 —— 订单超时延迟队列(DLX)、批量发券消息驱动、通知推送可靠投递（重试队列 + 轮询/降级兜底）。

## 常用命令

- 依赖：`docker-compose up -d`（ES 9200 + RabbitMQ 5672/15672）；MySQL 需本机已有（3306/homestay_db）
- 后端：`cd homestay-backend && mvn spring-boot:run`（8081）｜ `mvn test`（H2 内存库，见红线）
- 前端：`cd homestay-front && npm run dev`（5173）｜ `cd homestay-admin && npm run dev`（5174）｜ `npm run build`（vue-tsc 类型检查 + vite build）
- 一键：根目录 `npm run dev` / `npm run dev:admin` / `npm run dev:all`

## 架构概览

- homestay-front/ — C 端用户（Vue3 + TS + Element Plus + Vite + Pinia），5173
- homestay-admin/ — 管理后台（同栈），5174
- homestay-backend/ — 后端 API（Spring Boot 3.0.2 + Java 17 + JPA + Flyway + Security/JWT + Redis + ES + RabbitMQ），8081
- obsidian-vault/ — 项目文档（Obsidian 管理，git 同步 md 文档）
- 依赖：MySQL(3306, Flyway 管表结构) ｜ ES(9200, 需 IK 插件, **后端启动必须在线**) ｜ Redis(缓存) ｜ RabbitMQ(homestay-rabbitmq, homestay/homestay123, 管理台 15672)
- 端口：8080 常被本机 Dify 占用，后端固定 8081
## AI 客服 Agent 模块（三层架构）

> 设计文档：`obsidian-vault/04-架构分析/方案-AI客服Agent-三方权限矩阵.md`（v1.0 含落地记录）；测试报告：`obsidian-vault/04-架构分析/AI客服Agent-测试报告.md`。改动前必读。

- **第一层 FAQ**：`service/agent/tools/` 7 个只读工具 + `AgentToolRegistry`（10 工具白名单硬编码）+ `SupportAgentServiceImpl` 两阶段 JSON 编排 + `LlmClient`（OpenAI 兼容）
- **第二层 订单服务**：3 个申请型写工具（`request_user_refund`/`cancel_order_with_reason`/`raise_dispute_by_guest`）——**只起草不执行**，返回 `pendingAction`，用户确认后走 `POST /api/support/agent/confirm` 才真正执行；`OrderAccessGuard.requireGuestOrder` 强校验订单客人
- **第三层 争议辅助**：`DisputeAdvisorService.generateAdvice(orderId)` 给管理员生成裁决建议草稿（时间线+聊天摘要+相似案例+LLM 建议），**只建议绝不自动仲裁**
- 前端：`homestay-front/src/components/chat/SupportAgentDialog.vue`（确认卡片）+ `src/api/supportAgent.ts`
- 常见改动：新增工具 → 建类（实现 `AgentTool`）+ 在 `AgentToolRegistry` 构造函数注册 + 更新 `AgentWriteToolsTest`；改权限 → 动 `OrderAccessGuard`；改 LLM 提示 → `SupportAgentServiceImpl` 常量

## 行为准则

- **修复所有实例，不只修复报告的那一个。** 找到 bug 或值得改的模式后，全仓库 grep（`src/`、`test/`、`scripts/`）同类模式一并修复——一个修好的调用点配五个没动的兄弟是埋雷。
- **在共享层解决，不在调用点打补丁。** 动手前先问：这个修复该放公共 Service / 工具类 / 基类，还是调用点？先找已有 helper 再写新代码。但也不要为一个调用方硬造抽象。
- **修复让系统更简单。** 优先删除、合并代码，而不是加新层、新 flag、新特例。如果修复扩大了系统表面积，找那个能缩小它的版本。
- **按调用方判断影响面。** 一个被 50 处调用的 helper 改一行 ≠ 小改动。判断影响按调用方，不按 diff 大小。
- **改动要可验证。** 改后端跑相关 `mvn test`，改前端过 `npm run build` 类型检查；能起服务就冒烟一遍。别只改不验。

## 测试红线（强制）

> **没有 `@ActiveProfiles("test")` + 独立数据源(H2) 的 `@SpringBootTest`，一律视为生产环境炸弹。**

- 所有 `@SpringBootTest` 必须加 `@ActiveProfiles("test")`（application-test.properties = H2 内存库，禁止引用主库连接串）
- 禁止 `deleteAll()` / `truncate` / `drop` 真实表；写操作靠 `@Transactional` 自动回滚
- 跑 `mvn test` 前检查：①测试类有 test profile？②数据源是 H2？③有无危险清理操作？
- 历史事故：`ConcurrentBookingTest` 曾连真实 MySQL 执行 `deleteAll()` 清空全表数据

## 代码约定

- 分层：Controller → Service → Repository；出入参用 DTO，不直接暴露 Entity
- 表结构变更走 Flyway 新迁移文件（`db/migration/`），禁止手改表
- 前端组合式 API + `<script setup>`；Element Plus 自动导入，组件无需手动 import
- 提交信息：中文，`feat/fix/test/docs/chore` 前缀 + 模块，如 `feat(order): 订单超时延迟队列`

## 文档规范

vault 的 `功能模块-*.md` 有强制模板，完整规范见 `obsidian-vault/02-功能模块/_文档规范模板.md`。
核心四条：只记已实现；按用户视角分类；多用表格；组件结构树形缩进。

## 工具特有文件

CLAUDE.md / QWEN.md 只记录工具特有指令（graphify、Obsidian API 等），通用规范以本文件为准。
