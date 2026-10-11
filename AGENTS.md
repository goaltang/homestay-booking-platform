# homestay3

民宿预订系统（学习 + 面试项目）：C 端用户 + 管理后台 + Spring Boot 后端 + Obsidian 文档仓库。
亮点：RabbitMQ 三场景 —— 订单超时延迟队列(DLX)、批量发券消息驱动、通知推送可靠投递（重试队列 + 轮询/降级兜底）。

## 适用范围与事实来源

- 本文件维护共享的项目开发规范；个人学习计划、提醒任务和账号上下文放个人助手配置，不写入本文件。
- 用户本次明确指令优先于文件中的默认工作流程；工具专属文件不得覆盖共享的代码、测试和文档规则。
- 命令和依赖以 `package.json`、`pom.xml`、配置与 CI 文件为依据；文档和历史报告提供入口，不能代替当前代码核对。
- 安装详情见 [安装教程](docs/INSTALL.md)。本机路径、代理和历史故障记录有适用环境，使用前先核对。

## 常用命令

- 本地依赖：MySQL（3306/homestay_db）+ Redis；RabbitMQ 按需 `docker compose up -d rabbitmq`；ES 按需 `docker compose up -d elasticsearch`，后端设 `ELASTICSEARCH_ENABLED=true`
- 全套容器：`docker compose up -d --build` 默认不启动 ES 与监控；ES 使用 `search` profile 并设置 `ELASTICSEARCH_ENABLED=true`，监控使用 `monitoring` profile
- 后端：`cd homestay-backend && mvn spring-boot:run`（8081）｜ `mvn test`（H2 内存库，见红线）
- 前端：`cd homestay-front && npm run dev`（5173）｜ `cd homestay-admin && npm run dev`（5174）｜ `npm run build`（vue-tsc 类型检查 + vite build）
- 一键：根目录 `npm run dev` / `npm run dev:admin` / `npm run dev:all`

## 支付与网络环境坑（2026-08 实测踩坑）

- **WSL 直连支付宝沙箱网关超时**（2026-08 本机 Clash fake-ip 环境实测）：该环境下后端通过 JVM 参数 `-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7897` 调通支付；curl 同理走 `HTTPS_PROXY`。代理地址和端口先核对当前环境，不作为所有机器的必需配置。
- **支付宝 4 密钥体系**：应用私钥+应用公钥（自己生成的一对，工具目录 `密钥*` 文件夹）｜支付宝私钥（平台持有，不可见）｜支付宝公钥（平台固定派发，**不随应用公钥更换而变化**）
- **沙箱应用 ≠ 正式应用**：代码请求的是沙箱 APPID `9021000149671635`；密钥必须配在 open.alipay.com「沙箱环境」下的沙箱应用里，正式应用（如 2021005162691079）配了无效
- **验签排障**：`tools/alipay-sign-diagnose.py`（自动读密钥工具最新目录，直连网关判定是请求签名错还是响应验签错）

## 架构概览

- homestay-front/ — C 端用户（Vue3 + TS + Element Plus + Vite + Pinia），5173
- homestay-admin/ — 管理后台（同栈），5174
- homestay-backend/ — 后端 API（Spring Boot 3.0.2 + Java 17 + JPA + Flyway + Security/JWT + Redis + ES + RabbitMQ），8081
- obsidian-vault/ — 项目文档（Obsidian 管理，git 同步 md 文档）
- 依赖：MySQL(3306, Flyway 管表结构) ｜ ES(9200, 需 IK 插件, 默认关闭并走 JPA 搜索；开启时需在线) ｜ Redis(缓存) ｜ RabbitMQ(homestay-rabbitmq, homestay/homestay123, 管理台 15672)
- 端口：8080 常被本机 Dify 占用，后端固定 8081

## AI 客服 Agent 模块（三层架构）

> 设计文档：`obsidian-vault/03-技术设计/AI客服/AI客服Agent-权限与工具边界.md`（当前工具与权限边界）；测试报告：`obsidian-vault/04-验证与复盘/AI客服Agent-测试报告.md`。改动前必读。

- **第一层 FAQ**：`service/agent/tools/` 7 个只读工具 + `AgentToolRegistry`（10 工具白名单硬编码）+ `SupportAgentServiceImpl` 两阶段 JSON 编排 + `LlmClient`（OpenAI 兼容）
- **第二层 订单服务**：3 个申请型写工具（`request_user_refund`/`cancel_order_with_reason`/`raise_dispute_by_guest`）——**只起草不执行**，返回 `pendingAction`，用户确认后走 `POST /api/support/agent/confirm` 才真正执行；`OrderAccessGuard.requireGuestOrder` 强校验订单客人
- **第三层 争议辅助**：`DisputeAdvisorService.generateAdvice(orderId)` 给管理员生成裁决建议草稿（时间线+聊天摘要+相似案例+LLM 建议），**只建议绝不自动仲裁**
- 前端：`homestay-front/src/components/chat/SupportAgentDialog.vue`（确认卡片）+ `src/api/supportAgent.ts`
- 常见改动：新增工具 → 建类（实现 `AgentTool`）+ 在 `AgentToolRegistry` 构造函数注册 + 更新 `AgentWriteToolsTest`；改权限 → 动 `OrderAccessGuard`；改 LLM 提示 → `SupportAgentServiceImpl` 常量

## 行为准则

- **搜索所有同类实例，按业务语义决定修复范围。** 优先用 `rg` 搜索三端代码、测试和相关工具；确认属于同一缺陷、同一业务规则的实例一起修复。不同业务语义、无关重构和历史遗留问题单独记录，不混入本次改动。
- **在共享层解决，不在调用点打补丁。** 动手前先问：这个修复该放公共 Service / 工具类 / 基类，还是调用点？先找已有 helper 再写新代码。但也不要为一个调用方硬造抽象。
- **修复让系统更简单。** 优先删除、合并代码，而不是加新层、新 flag、新特例。如果修复扩大了系统表面积，找那个能缩小它的版本。
- **按调用方判断影响面。** 一个被 50 处调用的 helper 改一行 ≠ 小改动。判断影响按调用方，不按 diff 大小。
- **改动要可验证。** 按下面的验证要求执行，并报告实际命令、结果和未覆盖范围；构建通过不能代替行为或权限验证。

## AI 开发工作流程

1. 开始前检查当前分支、`git status` 和相关规范；区分已有修改与本次工作，不覆盖、回退或顺手提交用户的未提交修改。
2. 修改前阅读相关实现、调用方与测试。独立任务优先使用任务分支；已有指定分支或 worktree 时沿用，避免重复实现。
3. 按任务范围完成小而完整的改动。共享接口、权限、金额或状态流转变化时，检查所有受影响调用方和关联文档。
4. 执行相关验证并检查 diff。失败时区分本次引入、已有问题和环境限制，不通过删除测试、弱化断言或关闭检查掩盖失败。
5. 交付说明包含：变化及原因、验证命令与结果、未覆盖范围、Git 状态。未执行的检查明确写“未运行”，跳过不能写成通过。

提交、推送、合并和部署按用户已授权范围执行；授权已明确时直接完成，不重复确认。未获授权时先完成可审查的修改与验证，不擅自发布或改写已发布的 Git 历史。

## 验证要求

| 改动范围 | 最低验证要求 |
|---|---|
| 后端行为 | 在 `homestay-backend/` 跑相关 `mvn -Dtest=测试类名 test`；共享基础设施或跨模块修改扩大到 `mvn test` |
| C 端 / 管理后台 | 在受影响前端目录跑 `npm run build`；涉及两个前端时分别运行 |
| 两端已有测试覆盖的行为 | 加跑 `npm run test:run -- 测试文件路径`；交互修改在可用环境中验证关键操作 |
| Bug、权限、金额、状态流转 | 验证原触发场景与相关边界；必要时补行为回归测试，避免仅重复实现逻辑 |
| Flyway 迁移 | 验证独立数据库上的新库安装及旧版本升级；H2 测试关闭 Flyway，不能证明迁移通过 |
| 仅文档修改 | 检查路径、链接、命令依据及 `git diff --check`；无需仅为文档修改运行应用测试 |

两端都有 `lint:check`、`format:check`、`quality:changed`。改前端时运行全量 `lint:check`、`format:check` 和相关测试；`quality:changed` 另检查改动的共享配置与工具文件。CI 执行后端测试、两端全量 lint、全量格式检查、改动文件检查、Vitest 和构建。日常任务不夹带全仓格式化，专门的格式整理按用户授权单独提交。检查范围与工具配置见 [开发检查说明](docs/DEVELOPMENT.md)。

## 测试红线（强制）

> **所有 Spring 数据库集成测试必须使用 test profile 和独立测试数据源，禁止连接真实业务数据库。**

- 所有 `@SpringBootTest` 必须生效 `@ActiveProfiles("test")`（可通过测试基类继承）；其他加载 Spring 数据源的测试也遵守隔离要求。
- 当前默认数据源是 `src/test/resources/application-test.properties` 中的 H2 内存库。检查测试属性、动态属性和环境变量是否覆盖连接地址；不得引用主库连接串或凭据。
- Spring Boot 测试自动加载测试专用启动检查，缺少 test profile 或非 H2 内存地址时拒绝创建数据源。没有数据源的 MVC 切片测试不受影响；直接 JDBC、自定义数据源和非 Boot 测试仍需人工核对，不能把启动检查当成所有测试的安全证明。
- 普通数据库测试优先用 `@Transactional` 回滚。真实 HTTP、并发线程及 `REQUIRES_NEW` 写入可能独立提交，不能假定测试方法的事务覆盖这些写入；使用隔离测试数据并明确清理或销毁方式。
- 禁止对真实业务数据库执行 `deleteAll()`、`truncate`、`drop` 等破坏性操作。独立测试库可按场景清理；共享 H2 上下文优先只清理本测试数据，避免影响其他测试。
- 需要真实 MySQL 或 ES 行为时使用独立测试实例，如 Testcontainers，不复用开发或生产数据；无 Docker 导致跳过时报告未覆盖范围。
- 跑 Maven 测试前检查：①test profile 是否生效？②实际数据源是否隔离？③有无危险清理或独立提交？ES 集成测试还需 Docker 与测试镜像，见 CI 的镜像构建步骤。
- 历史事故：`ConcurrentBookingTest` 曾连真实 MySQL 执行 `deleteAll()` 清空全表数据

## 代码约定

- 分层：Controller → Service → Repository；出入参用 DTO，不直接暴露 Entity
- 表结构变更走 Flyway 新迁移文件（`db/migration/`），禁止手改业务表或修改已应用的历史迁移
- 前端组合式 API + `<script setup>`；Element Plus 自动导入，组件无需手动 import
- 提交信息：中文，`feat/fix/test/docs/chore` 前缀 + 模块，如 `feat(order): 订单超时延迟队列`
- 密钥、令牌与个人凭据只从本地配置或环境变量读取，不写入代码、文档、日志及提交内容。

## 文档规范

vault 的 `功能模块-*.md` 有强制模板，完整规范见 `obsidian-vault/98-文档规范/功能模块文档规范.md`。
核心四条：只记已实现；按用户视角分类；多用表格；组件结构树形缩进。
文档首页：`obsidian-vault/00-首页.md`；分类与维护规则：`obsidian-vault/98-文档规范/文档分类与维护规则.md`。
当前说明、历史报告、待实施方案和求职素材分开维护；整理日期不能代替代码核对日期或测试日期。

## 工具特有文件

CLAUDE.md / QWEN.md 只记录工具特有指令（graphify、Obsidian API 等），通用规范以本文件为准。

两个文件维护可进入版本管理的工具入口；本机详细设置放已忽略的 `CLAUDE.local.md`、`QWEN.local.md`。跨机器必需的共享约束写入 `AGENTS.md`；工具产物和本机配置保持本地维护，工具不可用时使用代码与仓库文档核对事实。
