# 开发检查与 AI 工具配置

共享行为规范见 [AGENTS.md](../AGENTS.md)，安装步骤见 [INSTALL.md](INSTALL.md)。本文说明仓库中的检查入口。

## 前端检查

以下命令在受影响的前端目录运行，两端都适用：

| 命令                                  | 范围                                                       |
| ------------------------------------- | ---------------------------------------------------------- |
| `npm run build`                       | 全量类型检查和生产构建                                     |
| `npm run lint:check`                  | 全量 ESLint，错误会使命令失败；存量警告仍显示              |
| `npm run format:check`                | 全量格式检查，包含存量问题                                 |
| `npm run quality:changed`             | 相对 HEAD 的已修改、暂存及未跟踪文件，执行 lint 和格式检查 |
| `npm run quality:changed -- 基准提交` | 相对指定基准的文件检查；CI 自动传入 PR 或 push 的基准提交  |

两端运行 `npm run test:run`，或 `npm run test:run -- 测试文件路径`。后台新增查询组件的行为测试，覆盖控件更新、数字范围、父页面接收与重置后搜索；并不代表所有后台页面已有测试覆盖。

两端 ESLint 共用 [规则文件](../tools/frontend-eslint-rules.mjs)，Prettier 共用根目录配置，编辑器使用根目录 `.editorconfig`。生成的声明文件、构建产物不进入格式检查。

CI 执行两端全量 lint、改动文件格式检查、Vitest、类型检查与构建。全量格式检查仍可手动执行。规则警告未升级为错误，也未通过全仓格式化清理历史问题。

2026-10-10 接入时，C 端全量 lint 为 0 错误、1097 警告，全量格式检查有 226 个存量文件未通过；管理后台全量 lint 为 19 错误、558 警告，涉及 props 修改、namespace、类型忽略注释及 `prefer-const`。这是接入前的本机基线。随后已修复后台全部 19 个错误，并启用两端全量 lint；未关闭或降级相关错误，历史警告继续显示。

## 后端测试隔离

在 `homestay-backend/` 运行 `mvn test`，或 `mvn -Dtest=测试类名 test`。

测试资源中的 `META-INF/spring.factories` 为 Spring Boot 测试自动注册 `TestDataSourceSafetyInitializer`。它在上下文刷新期间、创建数据源前检查 test profile 和 H2 内存连接地址（包括动态属性）；外部连接、Hikari 外部地址或 JNDI 覆盖会使启动失败。没有数据源的 MVC 切片测试不受此检查影响。异常不输出连接地址。

这个检查只保护通过 Spring Boot 启动的测试上下文。`SpringJUnitConfig`、直接 JDBC、自定义数据源 Bean 等仍需核对实际连接与隔离方式；它不能代替 [测试红线](../AGENTS.md#测试红线强制)。未来增加 MySQL 容器测试时，应为隔离容器建立明确验证方式，不能直接解除限制。

ES 集成测试使用独立 Testcontainers 实例；本地需要 Docker 和测试镜像，构建方式见 [CI 配置](../.github/workflows/ci.yml)。环境导致的跳过必须在交付中说明。H2 测试关闭 Flyway，迁移需要单独验证。

## 工具专属文件

[CLAUDE.md](../CLAUDE.md) 和 [QWEN.md](../QWEN.md) 是进入版本管理的工具入口，共享规则仍只有 AGENTS.md 一份。

本机 graphify、Obsidian 和记忆工具的详细设置可以保存在 `CLAUDE.local.md`、`QWEN.local.md`；两者被 Git 忽略，不能依赖它们约束新克隆或新 worktree。凭据只放环境变量或已忽略的本地配置。
