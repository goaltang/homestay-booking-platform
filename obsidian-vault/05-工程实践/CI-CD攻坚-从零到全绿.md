---
title: CI/CD 攻坚-从零到全绿
date: 2026-08-13
tags:
  - homestay
  - ci
  - github-actions
  - testcontainers
  - elasticsearch
  - interview
---

# CI/CD 攻坚-从零到全绿

> [!success] 一句话结论
> GitHub Actions 全绿（后端 429 测试含真实 ES 集成 + 前端双构建）。攻坚 16 个 commit，暴露并修复 **12 个本地开发环境掩盖的真实缺陷**——Windows/WSL 开发环境与 Linux CI、真实中间件的差异是这些 bug 的温床。

## 一、CI 架构

| 环节 | 内容 | 说明 |
|------|------|------|
| 后端测试 | ubuntu-latest + JDK17 + H2 内存库 | 零外部依赖，本地与 CI 同源跑 |
| ES 集成测试 | Testcontainers + 自建 `homestay-es-ik:test` 镜像 | CI 有 Docker 真跑，本地无 Docker 自动跳过 |
| 前端构建 | homestay-front / homestay-admin | Node20 + npm ci + vue-tsc + vite build |
| 镜像预构建 | workflow 内 `docker build` 装 IK 插件 | 避开 ImageFromDockerfile 限制与无 Docker 静态崩溃 |

## 二、12 个缺陷清单（面试素材）

| # | 缺陷 | 根因 | 本地掩盖原因 | 修复 |
|---|------|------|-------------|------|
| 1 | Linux 大小写编译失败 | `NotificationDto.java` 文件名与 `NotificationDTO` 类名不一致 | Windows 文件系统大小写不敏感 | `git mv` 重命名 |
| 2 | Redisson 无条件连 Redis | 主配置无条件装配，test profile 声称禁用但无条件装配 | 本地 Redis 常驻，连接成功掩盖 | `redisson.enabled` 条件装配 + test profile 关闭 + 全局 `RedissonTestConfig` mock（必须 `@Configuration + @Profile("test")`，`@TestConfiguration` 不被组件扫描） |
| 3 | `/actuator/prometheus` 404 | `application.properties` 被 gitignore → CI 无此文件 → management 配置缺失 | 本地有该文件 | 关键配置显式写进 `application-test.properties`（测试配置自包含原则） |
| 4 | `webEnvironment=NONE` 与 SecurityConfig 冲突 | `MvcRequestMatcher` 需要 web 环境 | 本地单跑该测试通过 | 改默认 MOCK 环境 |
| 5 | `User.role` not-null 约束 | 测试数据缺必填字段 | 本地跳过了 ES 测试未触发 | 测试补 `.role(...)` |
| 6 | 索引 0 条 | test profile 全局 `elasticsearch.enabled=false` 装配 NoOp 索引服务 | 本地 ES 测试被跳过 | `@TestPropertySource` 覆盖开启 ES + repository |
| 7 | 容器启动晚于上下文加载 | `@BeforeAll` 启动容器，Spring 上下文加载（repository 初始化连 ES）在前 | 本地跳过 | 标准 `@Testcontainers + @Container` 静态字段模式 |
| 8 | 官方 ES 镜像缺 IK 插件 | 生产索引用 `ik_max_word`，官方镜像无插件 | 本地用 Docker Compose 手动装过 IK | 自建 Dockerfile 装 IK（下载源用 infini 官方 CDN——GitHub 仓库已迁移、8.x release 资产丢失） |
| 9 | H2 保留字 `value` 建表失败 | `Amenity.value` 列名是 H2 保留字，随机 H2 URL 无 MODE=MySQL | 本地 H2 带 MODE=MySQL（随机 URL 才暴露） | 列名加反引号 `` `value` `` |
| 10 | OSIV 掩盖的懒加载炸 | 索引服务实体转换访问 amenities 懒加载集合，无事务依赖 OSIV 兜底 | 生产 HTTP 请求有 OSIV | 读方法加 `@Transactional(readOnly=true)` |
| 11 | **ES refresh 延迟** | 写入后立即搜索查不到（默认 refresh_interval=1s） | 本地无真 ES 集成测试 | 索引写入后显式 refresh |
| 12 | **Double.MAX_VALUE 序列化炸弹** | 无 maxPrice 时 `%.2f` 序列化成 312 位数字，ES JSON 解析失败 all shards failed | 本地从未走通 ES 搜索 | 无上限时省略 lte |
| 13 | **createdAt 纳秒拒写** | `LocalDateTime.now()` 带纳秒，ES mapping `date_hour_minute_second` 拒收 → 文档写不进索引、搜索永远 0 结果 | saveAll 静默成功，搜索 0 结果不报错 | 索引文档截断到秒精度 |
| 14 | **ES 路径懒加载炸** | `searchByElasticsearch` 的 `findAllById` 未预加载 owner（JPA 路径有 withDetailFetch），DTO 组装时 LazyInitializationException → 命中后返回空 | 生产 OSIV + 本地无真 ES | 搜索入口加 `@Transactional(readOnly=true)` |

> [!warning] 为什么是"12 个"？
> 第 9-10 条是同一轮修复链上的独立问题；真正阻断 CI 的核心缺陷是 1-8 + 11-14。全部是"Windows/本地环境能跑，Linux CI 必炸"的类型——本地开发环境越是"宽容"，越需要 CI 兜底。

## 三、关键调试方法论

1. **加日志看 queryJson 与完整异常堆栈**（`log.warn(msg, e)` 带堆栈 + 打印 query），而不是猜——all shards failed 的根因（312 位数字）就是日志里肉眼看到的。
2. **本地 docker run 起真 ES 容器 + curl 手动复现**，逐字段对照 CI 与手动差异：
   - createdAt 带纳秒写入 → `failed to parse field [createdAt] of type [date]`（实锤拒写）
   - 无纳秒 → `created`（实锤格式问题）
   - 同一查询手动 hits:1 而 CI 报错 → 排除查询本身，锁定数据格式
3. **disabledWithoutDocker 守卫**：本地无 Docker 自动跳过，CI 有 Docker 才真跑——集成测试的价值就在"CI 才跑"。
4. **小步提交**：一轮一个根因，push 后看 CI 日志精准抓错（`gh run view --log-failed`）。

## 四、本地无 Docker 的验证技巧

| 场景 | 做法 |
|------|------|
| Testcontainers 探测不到本地 Docker（Docker Desktop WSL 桥接） | 不强行修本地，依赖 CI 真跑；本地只验证编译 + 非 ES 测试 |
| Dockerfile 可用性 | `docker build` 手动构建镜像验证 |
| IK 插件是否装上 | `docker run --rm homestay-es-ik:test bin/elasticsearch-plugin list` → `analysis-ik` |
| 分词是否可用 | 起容器后 `curl _analyze?analyzer=ik_max_word` 验证 |
| 查询是否合法 | 手动建索引 + 写文档 + 完整 queryJson 复现 |

## 五、CI 文档与测试红线的关系

- 测试红线（`@ActiveProfiles("test")` + H2 + 禁 deleteAll 真表）是 CI 能安全跑的前提——CI 跑的就是这套约束下的 429 个测试。
- `application-test.properties` 已自包含：H2、redisson.enabled=false、management 端点、RabbitMQ 禁用、ES 开关。
- CI 是测试红线的最后守门人：本地"能过"不算数，Linux 上 H2 随机 URL + 真 ES 才是真相。

## 六、验证证据

- CI run `31619909978` = success（2m22s）：Backend 429/429 全过（含 `HomestaySearchServiceEsIntegrationTest` 2 个测试 17.65s 真跑）、Frontend 双构建通过。
- 本地基线：`mvn test` 429 全绿（ES 集成测试本地 Skipped: 2，符合预期）。
