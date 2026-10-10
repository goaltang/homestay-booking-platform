# PROJECT_NEXT_STEP — Homestay 下一阶段方向判断

> 生成时间：2026-08-13 ｜ 方式：只读探索（未修改任何源码/配置/文档，仅新增本报告）
> 结论先行：**停止开发新功能（放弃方向 A），主攻方向 D（部署 + 真实用户验证），并用方向 C/B 中"上线前必须"的最小集合开道。**

---

## 0. 一句话结论

这是一个**架构完整、功能严重过剩、工程质量中等、但完全没经过真实用户验证、且当前不具备安全公网部署条件**的个人项目。
你现在的瓶颈不是"功能不够"，而是"没有任何真实反馈，却在不断把代码堆到 10 万行"。
下一阶段最值得投入的方向是：**用约 1 周时间补上"上线前安全红线 + 可部署形态"，然后把系统放到公网、用 20~50 个真实房源和 5~10 个真实用户去验证核心闭环。** 继续开发新功能是当前最差的选择。

---

## 1. 项目当前状态总结

| 维度 | 状态 | 证据 |
|---|---|---|
| 代码规模 | 后端 src 约 7.1 万行 Java（main+test）、用户端 173 文件约 6.2 万行、管理端 77 文件约 2.6 万行 | `find ... wc -l` |
| 接口规模 | 58 个 Controller、400 个端点（GET 186/POST 130/PUT 43/DELETE 33/PATCH 8） | `obsidian-vault/03-技术设计/后端/后端-Controller 接口清单.md` + controller 目录 |
| 数据模型 | 51 个 JPA 实体；Flyway V1~V49 共 41 个迁移脚本 | `entity/`、`db/migration/` |
| 测试 | 62 个测试类、438 处 `@Test`（README 称 429）；9 个 `@SpringBootTest` 全部带 `@ActiveProfiles("test")`（红线守住了） | `src/test/` |
| 前端测试 | 用户端仅 2 个 vitest spec，管理端 0 | `homestay-front/src/**/*.spec.ts` |
| CI | GitHub Actions：后端 `mvn test` + 前端双 `npm run build`，全绿；**只有测试/构建，无部署流水线** | `.github/workflows/ci.yml` |
| 部署 | **没有后端/前端 Dockerfile，没有 application-prod.properties，docker-compose 只含 ES/RabbitMQ/Prometheus/Grafana（不含 MySQL/Redis/后端/前端）** | `docker-compose.yml`、`src/main/resources/` |
| 运行方式 | 后端 `mvn spring-boot:run`（8081）、前端 `npm run dev`（5173/5174）；MySQL/Redis 需本机自装 | `AGENTS.md`、`application.properties` |
| 支付 | 支付宝**沙箱**（含 `mock-success` 测试端点）；通知回调 URL 指向 8080（错端口） | `application.properties`、`PaymentController` |
| 提交历史 | 295 次 commit（README 写 267，已过期） | `git rev-list --count HEAD` |
| 文档 | Obsidian vault 50+ 篇，质量高，但与 README/代码有漂移（端口、commit 数、功能清单） | 前次探索报告 `tasks/2026-08-12-系统探索报告.md` |

**一句话状态**：这是一个"面试展示价值高、产品验证价值为零"的阶段。

---

## 2. 最重要的 10 个发现（均已核实到代码）

### 2.1【安全·严重】JWT 密钥硬编码且已进 git 历史，可伪造任意身份
- `JwtTokenProvider.java:24` 的 `@Value` 默认值就是真实密钥 `q8hVpEO8…（已轮换，见 D1 修复）`；
- 同一密钥还写进了 **被 git 跟踪** 的 `application-perf.properties:56`；
- `git log -S` 确认该密钥、以及支付宝沙箱私钥（`MIIEvg…（沙箱私钥已轮换/作废，见 D1 修复）
- 结论：任何拿到仓库的人都能签发合法的用户/管理员 JWT。**这是公网部署前的最高优先级红线。**

### 2.2【部署】完全没有生产形态
- 后端/前端/管理端都没有 Dockerfile；`application.properties` 是开发库（MySQL root/111111、Redis 000000）；
- 没有 `application-prod.properties`；CI 只测试不部署；`docker-compose.yml` 不含 MySQL/Redis/后端/前端。
- 结论：**"部署并找真实用户"不是切换配置就能做，而是要从零补一层可部署形态。**

### 2.3【体验·会真实崩】8080/8081 端口分裂，图片与 WebSocket 默认连错端口
- 后端实际跑 8081；但 `ImageUrlUtil.java:18` 默认 `app.server.url=http://localhost:8080`，而 `application.properties` **没有配置** `app.server.url` → 所有房源封面/图片/头像被组装成 `http://localhost:8080/...`（`HomestayDtoAssembler.java:248/580/615`）；
- 前端 6+ 处 fallback 也是 8080：`websocketService.ts:19-21`、`MapHomestayCard.vue:85`、`useMapSearch.ts:1133`、`HostLayout.vue:182`、`ProfileManage.vue:423`、`admin/utils/request.ts:21`；
- vite 代理只代理了 `/api` 和 `/uploads`，**没代理 `/ws`**。
- 结论：本机 8080 被 Dify 占用的情况下，上传的图片和实时通知/聊天大概率连不上。这是"首批真实用户立刻劝退"级问题。

### 2.4【安全·越权】支付入口不校验订单归属 + 测试后门留在生产代码
- `POST /api/payment/{orderId}/create` → `PaymentServiceImpl.generatePaymentQRCode(orderId, method)` **只按 id 查订单、不校验当前用户是否是订单客人**（对比：`POST /api/orders/{id}/pay` 路径有 `isOrderGuest` 校验）→ 越权可对他人订单发起支付、读取金额与房源标题；
- `GET /api/payment/*/status` 在 `SecurityConfig` 中 `permitAll` → 可枚举订单 id 探测支付状态；
- `POST /api/payment/{orderId}/mock-success`（仅 `@PreAuthorize("hasRole('ADMIN')")`，**没有 profile 隔离**）可直接把订单置为已支付，生产代码里留着测试后门。

### 2.5【安全·信息泄露】异常处理的"生产/开发"判断是坏的，生产也会泄露内部错误
- `GlobalExceptionHandler` 多处用 `System.getProperty("spring.profiles.active", "dev")` 判断环境；
- 但 Spring Boot 正常启动（`--spring.profiles.active=prod` 或环境变量）**不会设置这个 JVM 系统属性** → 该值恒等于 `"dev"` → 生产环境也会把 `ex.getMessage()` 和异常类名放进响应；
- 叠加 `application.properties` 里 `server.error.include-message=always`、`include-binding-errors=always`，未覆盖路径的默认错误页也会带消息。

### 2.6【性能·N+1】ES 搜索路径存在 N+1，且是压测盲区
- `HomestaySearchServiceImpl:1058` 走 ES 时用 `homestayRepository.findAllById(homestayIds)`（**无 fetch join / 无 @EntityGraph**），随后 DTO 组装懒加载 `owner`/`amenities`/`images`（`HomestayDtoAssembler:580-615`）；
- ES 查询一次性取 `PageRequest.of(0, 10000)`，冲突过滤后**在内存里分页**（`searchHomestayPage:279`）；
- 压测基线明确写了"ES 禁用（搜索链路未覆盖）"（`性能压测报告-系统容量基线.md`）。JPA 路径有 `withDetailFetch` 兜底，ES 路径没有。

### 2.7【技术债·全局】懒加载被"全局兜底"合法化，N+1 温床
- `application.properties` 设了 `hibernate.enable_lazy_load_no_trans=true`，且未显式关闭 OSIV（Spring Boot 默认开启）；
- 结果：Web 请求里懒加载永不报错，于是到处可以直接 `order.getHomestay().getTitle()`、`homestay.getOwner().getAvatar()`，N+1 被无声放大；
- CI 攻坚已多次暴露同一病根（无事务/异步场景 `LazyInitializationException`），修复方式是逐点加 `@Transactional(readOnly)`，属于"打补丁"而非"治本"。

### 2.8【测试】结构不错，但覆盖严重不均
- 好的部分：Agent 工具/权限、MQ 消费者幂等、并发防超卖、通知枚举迁移、ES 集成（Testcontainers）都有测试，CI 全绿；
- 缺口：`HomestayQueryService`、`HomestayRecommendationService`、`ReviewService`、`HostService`、`EarningService`、`ChatService`、`FileService`、`StatisticsService`、`UserService`、`VerificationService`、`PricingService`（外层）**均无专门测试类**；前端几乎零测试；无 E2E。

### 2.9【技术债·可维护性】God class 与 AI 编码残留
- `OrderLifecycleServiceImpl.java` 1663 行、`OrderServiceImpl.java` 1128 行、`HomestaySearchServiceImpl.java` 1267 行、`HomestayDtoAssembler.java` 677 行；
- `OrderLifecycleServiceImpl.java:830-836` 残留 AI 对话过程的占位注释（"We'll need to implement… placeholder… change strategy…"），混在生产代码里；
- 种子/初始化有 4 套机制并存：`DataInitializer`（设施）、`DatabaseInitializer`+`db/init.sql`（admin 表**在 Flyway 外建表**）、`data.sql`（遗留）、`db/seed_data.sql`（**含 `TRUNCATE TABLE users/homestays`** 的危险手工脚本）。

### 2.10【产品】核心商业闭环从未被真实验证
- 支付 = 支付宝沙箱；实名认证 = "上传资料 + 管理员人工审核"（非真实身份核验）；邮件 = `your-email@gmail.com` 占位符；
- 默认演示账号 `user/111111`、`host/111111`、`admin/admin888` 在启动时自动创建（`AdminServiceImpl:70-110`，`@Profile("!test")`）；
- 没有真实房源、真实房东、真实支付、真实评价的任何一条闭环数据。

---

## 3. 最值得解决的 5 个问题（按优先级）

| # | 问题 | 为什么是它 | 落点 |
|---|---|---|---|
| 1 | **密钥/口令全部轮换并环境变量化** | 不解决就无法安全公网部署，其余都免谈 | JWT secret 进 env、移除 git 历史中的支付宝沙箱私钥（轮换）、prod 路径不自动创建演示账号、收紧 MySQL/Redis/MQ/Grafana 默认口令 |
| 2 | **8080/8081 端口分裂** | 图片 + WebSocket 默认连错端口，首批真实用户立刻遇到 | 后端 `app.server.url` 改成正确值或输出相对路径；前端 6 处 8080 fallback 改 8081/相对路径；vite 加 `/ws` 代理；支付宝 notify-url 改 8081 |
| 3 | **支付/订单越权 + 测试后门** | 资金链路 + 越权，是产品与安全双重红线 | `generatePaymentQRCode` 加订单归属校验（复用 `OrderAccessGuard`）；`/payment/*/status` 收口为登录态；`mock-success` 用 `@Profile` 隔离或直接删除 |
| 4 | **异常处理信息泄露** | 生产会泄露内部错误细节，攻击者用于探测 | 用 `Environment.getActiveProfiles()` 或 `@Profile` 判断，生产一律不返回 detail；`server.error.include-*` 改 `never` |
| 5 | **ES 搜索路径 N+1 + 未压测** | 搜索是 C 端最高频路径，却被压测基线排除 | `findAllById` 前按 ID 预加载（fetch join/@EntityGraph/批量查询），分页下推到 ES；把 ES 路径纳入 k6 压测 |

> 说明：这 5 个全部是"上线前必须"，不需要动大架构，2~4 天可完成。

---

## 4. 最值得开发的 3 个功能（如果确实有必要）

**先给结论：在验证阶段，不建议开发任何大的业务新功能。** 如果一定要写代码，只写"验证基础设施"，不写新模块：

1. **最小用户行为漏斗看板（验证用）**：复用已有 `BehaviorTrackingController` + `UserBehaviorEvent`，只补一个"注册→搜索→详情→下单→支付→评价"的每日漏斗 SQL/页面。目的不是炫技，而是让你和房东能看到真实用户卡在哪一步。
2. **房东真实房源上架的可用性打磨（不是新功能，是打通已有功能）**：把 `HostHomestayForm` 的图片上传（受 8080 bug 影响）、草稿→提交审核→上架这条链在真实环境走通。供给侧是冷启动的命门。
3. **公网部署后必需的运维最小集（不是业务功能）**：健康检查 + 启动失败告警 + 上传文件备份。让公网实例"挂了你能知道"，避免真实用户在无人看管时全军覆没。

> 反例（明确**不要**现在做）：A/B 测试框架优化、更多推荐策略、动态定价细化、AI 客服第二/三层扩展、多租户 SaaS 化、SSR/SEO。这些在 0 用户时都是无验证的复杂度。

---

## 5. 最值得做的 3 个性能/技术优化

1. **消除 ES 搜索路径的 N+1，并把 ES 纳入压测**：`HomestaySearchServiceImpl:1058` 用批量预加载替代 `findAllById` 后的懒加载；ES 侧用真实分页（`from/size`）替代"取 10000 条内存分页"。这是唯一一条"性能 + 正确性"同时受益的优化。
2. **移除 `enable_lazy_load_no_trans`，显式管理懒加载边界**：短期在 DTO 组装边界统一 `@Transactional(readOnly)` + fetch plan；长期删掉该全局开关，让 N+1 和 LazyInitializationException 暴露出来而不是被掩盖。比任何缓存优化都更治本。
3. **缓存策略收敛**：当前 Caffeine（业务缓存）+ Redis（锁/限流/geocoding/POI）并存，`CacheController:36/89` 用 `redisTemplate.keys("*")`（阻塞式 KEYS，应改 SCAN）。单实例期先保持 Caffeine 并清理 key 命名；多实例化前再决定是否把业务缓存迁 Redis。

> 顺序：先 1（直接影响 C 端体验），再 2（长期债务），3 可延后。

---

## 6. 当前最严重的 3 个技术风险

1. **认证体系可被攻破**：JWT 密钥在源码默认值、git 跟踪的 perf 配置、git 历史中三处泄露 → 可伪造管理员 token；默认演示账号在启动时自动创建且口令已知（admin/admin888、user/111111、host/111111）。
2. **没有可落地的生产形态 + 依赖版本 EOL**：无 Dockerfile/无 prod 配置/无部署流水线；Spring Boot 3.0.2（Spring Framework 6.0.x）已停止 OSS 维护，存在已知 CVE。就算今天想上线，也"上不出去、上出去也不安全"。
3. **资金链路从未真实验证**：支付仅沙箱、`mock-success` 后门留在生产代码、退款/结算/平台分成（`app.earnings.host-share-rate=0.80`）没有真实对账。真实资金一旦流入，账务风险极高。

---

## 7. 当前最严重的 3 个产品风险

1. **功能严重过剩、供需两端零验证**：25 模块 / 400 端点，A/B 测试、个性化推荐、动态定价、AI 客服这些"高级功能"全部建立在没有真实用户反馈的假设上。真实用户很可能会告诉你：这些都不是他们最先需要的东西。
2. **供给侧冷启动才是真正瓶颈**：没有真实房源/真实房东，C 端再完善也是空转。而"实名认证=人工审核、支付=沙箱、收益未真实验证"让房东无法完成一次真实的"上架→接单→收款→提现"，供给端信任无法建立。
3. **首次使用即崩的体验会劝退且不可挽回**：图片/WebSocket 端口错误、无 prod 配置、演示账号混淆、支付沙箱限制——任何一个都会让第一批真实用户流失，而且你拿不到任何有效反馈。

---

## 8. 明确判断：现在该不该继续开发新功能？

**不该。明确选择方向 D（停止开发功能，部署并寻找真实用户验证）。**

- 方向 A（新功能）：**否决**。功能供给已远超验证进度，再加功能只会让"无验证的复杂度"更高。
- 方向 B（优化已有功能）：**只做上线前最小集**（本报告第 3 节），不做全面优化。
- 方向 C（补测试/提质量）：**只补"安全红线 + 关键资金路径"的测试**，不追求覆盖率数字。
- 方向 D（部署 + 真实用户验证）：**主线**。

理由：你已经投入 16 个月、295 次 commit、10 万+ 行代码，却没有任何一条真实用户反馈。继续写代码的边际收益趋近于零；而哪怕只有 10 个真实用户，也能立刻告诉你"搜索、下单、支付、房东上架"里哪一环是假的。**先验证，再决定是否值得继续投入，而不是继续堆功能。**

---

## 9. 未来 7 天 20 小时执行计划

> 目标：7 天后系统能在一个公网 URL 上被 5~10 个真实用户使用，并回收第一轮真实反馈。

| 天 | 时长 | 做什么 | 完成标准 |
|---|---|---|---|
| D1 | 4h | 安全轮换：JWT secret 环境变量化并换新值；轮换/移除支付宝沙箱私钥；prod 路径不自动创建演示账号；修 `GlobalExceptionHandler` 的 profile 判定；`server.error.include-*` 收紧 | `mvn test` 全绿；grep 源码不再出现真实密钥 |
| D2 | 3h | 修复端口与越权：`app.server.url`/`ImageUrlUtil`、前端 6 处 8080 fallback、vite `/ws` 代理、`generatePaymentQRCode` 归属校验、`/payment/*/status` 收口、`mock-success` profile 隔离 | 本地起服务，图片/WebSocket/支付创建均正确 |
| D3 | 3h | 补可部署形态：后端 Dockerfile（jar）+ 前端/管理端 nginx 静态 Dockerfile + 完整 `docker-compose`（MySQL/Redis/ES/IK/MQ/后端/前端/管理端）+ `application-prod.properties`（全 env 注入） | 本机 `docker compose up` 起一整套 |
| D4 | 2h | 部署到公网 VPS（或 Railway/Render），配域名 + HTTPS + 反向代理；ES 装 IK | 手机流量能打开首页、能注册登录 |
| D5 | 3h | 灌真实种子：你所在城市 20~50 个真实房源（真实照片/描述/坐标）；自己+朋友走完 注册→搜索→详情→下单→沙箱支付→入住→评价 全流程并录下卡点 | 有一条真实完整订单闭环 |
| D6 | 3h | 拉 5~10 个真实用户内测（2~3 房东 + 5~7 房客）；用行为埋点 + 第 4 节的最小漏斗看卡点；修 P0 | 收到 ≥5 条真实反馈，≥2 个用户走完主流程 |
| D7 | 2h | 复盘：把反馈归成"必须修 / 可延后 / 该砍掉"，产出下一阶段 backlog；决策是否继续投入 | 一份 1 页结论，替代"继续堆功能" |

总计：4+3+3+2+3+3+2 = 20h。

---

## 10. 最重要的一项：明天醒来后，如果只能做一件事，做什么？

> **把 JWT 密钥、支付宝沙箱私钥从代码/配置/git 历史中剥离并轮换，改为环境变量注入；同时让生产路径不再自动创建 `admin/admin888`、`user/111111`、`host/111111` 这些演示账号。**

为什么是这一件：
1. 它是你从"学习项目"跨到"可公网验证产品"的**唯一硬门槛**——不解决它，任何部署动作都是把可被伪造身份的入口暴露到公网；
2. 它不依赖任何新功能、不依赖外部资源，今天就能完成，且完成标准明确（`mvn test` 全绿 + 源码/跟踪文件里搜不到真实密钥）；
3. 它逼你迈出方向 D 的第一步，打破"再优化一轮就上线"的循环。

做完这一件之后，再按第 9 节的计划推进：修端口/越权 → 补 Dockerfile → 公网部署 → 真实用户。**在那之前，不要写任何一行新功能代码。**

---

### 附：主要证据索引（供你自行复核）

| 结论 | 证据位置 |
|---|---|
| JWT 密钥硬编码/进 git | `security/JwtTokenProvider.java:24`；`resources/application-perf.properties:56`；`git log -S 'q8hVpEO8…'` |
| 支付宝沙箱私钥进历史 | `git log -S 'MIIEvg…（沙箱私钥已轮换/作废，见 D1 修复） |
| 默认演示账号自动创建 | `service/impl/AdminServiceImpl.java:70-110` |
| 端口 8080/8081 分裂 | `util/ImageUrlUtil.java:18`；`services/websocketService.ts:19-21`；`admin/utils/request.ts:21`；`homestay-front/vite.config.ts`（只代理 /api、/uploads） |
| 支付越权 + 后门 | `service/impl/PaymentServiceImpl.java:55-135`（无归属校验）；`controller/PaymentController.java:103-120`（mock-success） |
| 异常处理 profile 判定错误 | `exception/GlobalExceptionHandler.java` 多处 `System.getProperty("spring.profiles.active","dev")` |
| ES 路径 N+1/内存分页 | `service/impl/HomestaySearchServiceImpl.java:1058`、`279`；`HomestayDtoAssembler.java:580-615` |
| 全局懒加载兜底 | `resources/application.properties`（`hibernate.enable_lazy_load_no_trans=true`，未关闭 OSIV） |
| 测试覆盖缺口 | `src/test/` 中无 HomestayQuery/Recommendation/Review/Host/Earning/Chat/File/Statistics/User/Verification/Pricing 专门测试 |
| God class + AI 残留 | `service/impl/OrderLifecycleServiceImpl.java`（1663 行，830-836 残留注释） |
| 多套种子/初始化 | `config/DataInitializer.java`、`config/DatabaseInitializer.java`、`db/init.sql`、`db/seed_data.sql`（含 TRUNCATE）、`data.sql` |
| 无生产部署形态 | 根目录 `docker-compose.yml`（无 MySQL/Redis/后端/前端）；无 `application-prod.properties`；无后端/前端 Dockerfile |
| 依赖 EOL | `homestay-backend/pom.xml`：`spring-boot.version=3.0.2` |

---

*本报告全程只读，未修改任何既有源码、配置、文档或数据；仅新增 `PROJECT_NEXT_STEP.md` 一个文件。*
