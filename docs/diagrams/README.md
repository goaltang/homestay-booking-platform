# 系统架构图集

用浏览器打开 `system-overview.html`。Windows 路径：`D:\homestay3\docs\diagrams\system-overview.html`。窄屏可在图内横向滚动。

这张图使用 diagram-design 默认浅色风格，采用 `slide-16x9` 画布（1280 × 720），展示当前项目的逻辑架构。

| 文件 | 用途 |
|---|---|
| [system-overview.html](system-overview.html) | 可修改源文件，下载后用浏览器打开 |
| [system-overview.png](system-overview.png) | 2560 × 1440 高清图片，供根目录中英文 README 嵌入 |

修改 HTML 后需重新导出 PNG，让 README 中的图片与源文件保持一致。PNG 只包含图本身，HTML 页面的标题和下方说明不包含在图片内。

## 图集导航

共 35 张图：1 张总览 + 34 张详图。Windows 浏览器入口：`D:\homestay3\docs\diagrams\index.html`。HTML 提供范围说明与代码入口；PNG 用于 GitHub、Obsidian 和演示文稿。

| 图 | HTML 与说明 | 高清 PNG |
|---|---|---|
| 系统总览 | [HTML](system-overview.html) | [PNG](system-overview.png) |
| 订单生命周期 | [HTML](order-lifecycle.html) | [PNG](order-lifecycle.png) |
| 退款与争议 | [HTML](refund-dispute.html) | [PNG](refund-dispute.png) |
| 下单事务时序 | [HTML](booking-sequence.html) | [PNG](booking-sequence.png) |
| 支付宝支付时序 | [HTML](payment-sequence.html) | [PNG](payment-sequence.png) |
| MQ 订单超时 | [HTML](mq-order-timeout.html) | [PNG](mq-order-timeout.png) |
| MQ 批量发券 | [HTML](mq-coupon-batch.html) | [PNG](mq-coupon-batch.png) |
| MQ 通知推送 | [HTML](mq-notification.html) | [PNG](mq-notification.png) |
| AI 客服编排与确认 | [HTML](agent-workflow.html) | [PNG](agent-workflow.png) |
| 统一计价 | [HTML](pricing-flow.html) | [PNG](pricing-flow.png) |
| 预订与支付实体关系 | [HTML](er-booking.html) | [PNG](er-booking.png) |
| 优惠券实体关系 | [HTML](er-coupons.html) | [PNG](er-coupons.png) |
| 搜索与推荐 · 两条独立链路 | [HTML](search-recommendation.html) | [PNG](search-recommendation.png) |
| 动态定价规则 · 匹配、顺序与停止 | [HTML](pricing-rules.html) | [PNG](pricing-rules.png) |
| Docker 配置拓扑 · 应用入口 | [HTML](deployment-apps.html) | [PNG](deployment-apps.png) |
| Docker 配置拓扑 · 数据服务 | [HTML](deployment-data.html) | [PNG](deployment-data.png) |
| 并发预订 · 日期锁与事务边界 | [HTML](booking-concurrency.html) | [PNG](booking-concurrency.png) |
| 认证与权限 · 校验分层和覆盖边界 | [HTML](auth-permissions.html) | [PNG](auth-permissions.png) |
| 三方业务泳道 · 房客、房东与管理员 | [HTML](business-swimlane.html) | [PNG](business-swimlane.png) |
| 入住与退房 · 押金和结算边界 | [HTML](stay-settlement.html) | [PNG](stay-settlement.png) |
| 聊天消息 · 保存、实时推送与已读 | [HTML](chat-sequence.html) | [PNG](chat-sequence.png) |
| 房源审核 · 提交、撤回与重新审核 | [HTML](homestay-audit.html) | [PNG](homestay-audit.png) |
| 房源索引 · 增量、删除与重建 | [HTML](index-sync.html) | [PNG](index-sync.png) |
| 房东日历 · 数据合成与库存操作 | [HTML](calendar-inventory.html) | [PNG](calendar-inventory.png) |
| 营销活动 · 状态、预算与使用流水 | [HTML](campaign-lifecycle.html) | [PNG](campaign-lifecycle.png) |
| 房东收益 · 生成与记录结算 | [HTML](host-earnings.html) | [PNG](host-earnings.png) |
| C 端前端 · 页面、状态与通信 | [HTML](frontend-collaboration.html) | [PNG](frontend-collaboration.png) |
| 文件上传 · 本地存储与访问路径 | [HTML](file-upload.html) | [PNG](file-upload.png) |
| 三角色用例 · 能力与权限边界 | [HTML](role-usecases.html) | [PNG](role-usecases.png) |
| 后端服务依赖 · 订单子系统 | [HTML](backend-dependencies.html) | [PNG](backend-dependencies.png) |
| 数据库物理结构 · 预订与支付快照 | [HTML](db-booking-physical.html) | [PNG](db-booking-physical.png) |
| 订单全链路 · 跨模块协作总图 | [HTML](order-end-to-end.html) | [PNG](order-end-to-end.png) |
| 交易一致性 · 事务边界与补偿总图 | [HTML](transaction-consistency.html) | [PNG](transaction-consistency.png) |

| AI 客服 · 编排、权限与用户确认 | [HTML](agent-control-boundaries.html) | [PNG](agent-control-boundaries.png) |
| 订单状态联动 · 支付、优惠与收益 | [HTML](order-state-coupling.html) | [PNG](order-state-coupling.png) |

31 张基础图使用 `slide-16x9` 画布，PNG 为 2560 × 1440；4 张复杂总图使用 1800 × 1120 大画布，PNG 为 3600 × 2240，可在浏览器中横向滚动并使用浏览器缩放。PNG 在当前环境中使用本机中文备用字体，避免展示时依赖在线字体；HTML 保留在线字体与本地字体回退。

## 绘图核对中发现的实现限制

| 位置 | 当前代码行为 | 图中处理 |
|---|---|---|
| 仲裁批准 | `OrderStatusUpdater.markDisputePending` 将 paymentStatus 设为 `DISPUTED`；`DisputeServiceImpl.resolveDispute(APPROVED)` 调用只接受 `REFUND_PENDING` 的 `PaymentProcessingServiceImpl.approveRefund` | 标注前置冲突，画为调用步骤，不连成退款成功 |
| 争议单测 | 批准路径 mock 了 `approveRefund` | 不据此宣称跨服务批准路径通过 |
| 通知发布 | 数据库提交与 MQ 发布间没有 outbox；生产者失败记录日志 | 不宣称崩溃绝不丢消息 |
| 发券失败 | 单条明细失败被记录；消息级异常才走重试队列 | 分开描述明细失败与消息重试 |
| 发券任务扫描 | 扫描超时 `PENDING`，不扫描 `PROCESSING` | 不宣称覆盖所有卡住任务 |
| 预订锁与事务 | 外层下单事务尚未提交时，safeCreateOrder 的 finally 已释放日期锁；显式租期 30 秒 | 展示一种可能交错，不宣称整个事务受锁保护或绝不超卖 |
| 订单工具权限 | OrderAccessGuard 用于 Agent 工具，其他业务自行校验 | 不画成全站必经统一 Guard |
| 押金退还 | processDeposit 的 REFUND 更新记录，实际网关退款仍为 TODO | 不将押金记录 REFUNDED 画成真实资金退款 |
| 聊天推送 | 事务内直接推送，异常记录日志；未走通知 MQ | 不宣称提交后可靠投递 |
| 聊天归属 | 四个按会话 ID 操作未见服务参与者归属校验 | 不添加不存在的 Guard；仅记录，未修改实现 |
| 索引同步 | 单房源异步同步直接触发；重建删旧索引再创建 | 不宣称提交后强一致或无查询空窗 |
| 日期库存 | markAsAvailable 未见调用；占用写入发生在订单保存前 | 不画自动释放保证或可靠绑定订单 ID |
| 活动预算 | 回退失败有异常/返回值边界；不自动重启 ENDED | 画为尝试补偿，不宣称必然成功 |
| 房东收益 | 支付/退房可生成收益；结算只更新 SETTLED，未检查退房日期 | 不等同真实资金到账 |
| 文件上传 | MIME 元信息检查、本地存储；头像更新失败可不阻止上传 | 不宣称内容检测或文件与业务记录原子提交 |
| 物理表结构 | schema.sql 明确剥离外键/CHECK；部分历史迁移含 FK | 按快照不画 FK，注明初始化路径差异；未查运行库 |
| 实体连线 | 部分普通 Long ID 字段没有 JPA 关联 | 说明为逻辑实体关系，不作为物理 FK 图 |

这些限制按当前代码静态核对；本轮只绘图和更新文档，没有修改业务实现或执行真实支付联调。

## 新增图的核对范围

搜索与推荐为独立链路；列表与分页搜索对 ES 空结果的回退行为不同。定价按 priority 升序执行，作用域不代表覆盖顺序。Docker 两图依据 Compose / Nginx 配置，未运行验证；容器入口为 8088 / 8089，本地 Vite 开发端口仍为 5173 / 5174。RabbitMQ 当前未配置持久化卷。

## 如何更新

修改对应 HTML 内联 SVG 后，按 diagram-design 导出规范重新导出 PNG，再同步 README 链接。浏览器图集 `index.html` 用于本地查看；GitHub README 通过 PNG 展示，不直接运行 HTML。

## 总览取舍

| 内容 | 处理 |
|---|---|
| 房客端、房东端 | 合并为实际承载两种角色的 `homestay-front` |
| 管理端 | 保留为独立前端应用 |
| 后端业务模块 | 合并进一个 Spring Boot 应用节点，用文字分区表达职责 |
| 数据设施 | 分别保留 MySQL、Redis、Elasticsearch、RabbitMQ |
| 外部服务 | 保留支付宝沙箱和 OpenAI 兼容大模型接口 |
| MQ 队列、重试、消费者 | 总览仅表达 AMQP 集成，详细路径留给 MQ 详图 |
| Agent 工具及确认流程 | 总览标明权限边界，内部调用留给 Agent 详图 |
| 高德地图、文件上传 | 当前已实现，但本图不展开；它们不是不存在的功能 |
| 主机、容器、代理、生产端口 | 属于部署拓扑，不在逻辑总览中表达 |

与旧 `docs/architecture.drawio` 相比，重点更新是合并房客/房东前端边界，并补充 RabbitMQ 与大模型集成。旧图保持原样。

## 内容核对入口

| 图中关系 | 代码或文档入口 |
|---|---|
| C 端同时承载房客/房东 | `homestay-front/src/router/index.ts` |
| 数据访问、锁、检索 | 后端 `repository/`、`config/RedissonConfig.java`、`service/impl/HomestaySearchServiceImpl.java` |
| MQ 三场景 | 后端 `mq/`、`config/*MQConfig.java`、`service/impl/OrderLifecycleServiceImpl.java`、`service/impl/CouponBatchIssueServiceImpl.java` |
| 通知事件与推送 | 后端 `service/impl/NotificationWebSocketEventListener.java`、前端 `src/services/websocketService.ts` |
| 工具白名单和确认 | 后端 `service/agent/AgentToolRegistry.java`、`service/agent/impl/SupportAgentServiceImpl.java` |
| 争议建议 | 后端 `service/impl/DisputeAdvisorServiceImpl.java` |
| 支付网关与回调 | 后端 `service/gateway/`、`controller/PaymentController.java` |

MQ 的发布与消费箭头表达集成方向，不代表端到端严格一次投递；可靠性需结合各场景的重试、幂等及兜底实现评估。
