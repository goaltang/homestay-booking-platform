---
title: 方案-AI客服Agent-三方权限矩阵
date: 2026-08-06
tags:
  - homestay
  - agent
  - design
---
# AI 客服 Agent：三方权限矩阵与工具边界（v0.2 定稿）

> [!info] 背景
> homestay3 计划新增 AI 客服 Agent 模块。本文档从现有 service 层代码推导出房客/房东/管理员三方权限矩阵，并据此划定 agent 的工具边界。这是 agent 模块的权限地基，所有工具注册以此为准。

## 一、两项设计决定（2026-08-06 拍板）

### 决定 1：押金 RETAIN（扣押）的定位
**场景定义**：RETAIN 仅用于"房客造成的实际损失"——物品损坏、违规吸烟、宠物破坏、钥匙丢失等，由房东发起并附说明（processDeposit 的 note 字段）。

**Agent 的角色**：只读不碰。
- agent 可以读退房记录，回答客人"我的押金怎么还没退"——解释当前状态（待退还 / 已扣押及原因）
- agent **绝不执行** processDeposit 的任何动作（COLLECT/REFUND/RETAIN/WAIVE 全部禁止）
- agent 引导客人走正当路径：对扣押有异议 → 发起争议

**理由**：押金是碰钱操作里争议成本最高的一种（涉及实物损失举证），行业标杆（Airbnb）也把押金纠纷单独走 Resolution Center，不让自动化流程插手。

### 决定 2：补上"房客发起争议"接口
**现状缺口**：DisputeService.raiseDispute 只允许房东对退款拒绝发起争议，房客没有对称的入口——客人退款被拒后无路可走，只能找管理员，而系统里房客→管理员没有直接通道。

**决定**：补上房客侧争议入口，这是 agent 模块的关键落点：
- 新增 `raiseDisputeByGuest(orderId, reason)`：客人对"退款被拒/押金扣押/入住体验严重不符"发起争议，订单进入 DISPUTE_PENDING，管理员仲裁
- agent 的价值点：客人退款被拒时，agent 自动判断是否符合争议条件 → 汇总订单时间线+聊天记录 → **代客人起草争议申请**（客人确认后提交）
- 与行业对齐：Airbnb Resolution Center 是双向的，房客是押金/赔偿争议的主要发起方

**理由**：这不是打补丁，而是让争议机制从"房东单向保护"变成"双向对称"，答辩时是"发现权限设计不对称并修正"的完整故事。

## 二、三方权限矩阵 v0.2（定稿）

图例：✅可直接执行 🔶可申请待审批 👁只读 ❌禁止

### 订单生命周期
| 操作 | 房客 | 房东 | 管理员 |
|---|---|---|---|
| 创建订单 | ✅ createOrder | ❌ | ❌ |
| 支付订单 | ✅ payOrder | ❌ | 🔶 confirmPayment（异常兜底） |
| 确认/拒绝订单 | ❌ | ✅ confirmOrder/rejectOrder | ❌ |
| 取消订单 | ✅ cancelOrderWithReason | ✅ rejectOrder 变相取消 | ✅ systemCancelOrder |

### 退款（Agent 最高危区域）
| 操作 | 房客 | 房东 | 管理员 |
|---|---|---|---|
| 申请退款 | ✅ requestUserRefund | ❌ | ❌ |
| 批准/拒绝退款 | ❌ | ✅ approveRefund/rejectRefund | ✅ 同左 |
| 直接执行退款 | ❌ | ❌ | ✅ executeRefund（ADMIN_INITIATED） |
| 退款预览 | 👁 getRefundPreview | 👁 | 👁 |

### 争议
| 操作 | 房客 | 房东 | 管理员 |
|---|---|---|---|
| 发起争议 | 🔶 raiseDisputeByGuest **（本次新增）** | ✅ raiseDispute | ❌ |
| 仲裁争议 | ❌ | ❌ | ✅ resolveDispute |

### 入住/退房
| 操作 | 房客 | 房东 | 管理员 |
|---|---|---|---|
| 生成入住凭证 | ❌ | ✅ prepareCheckIn | ❌ |
| 自助入住 | ✅ selfCheckIn（凭码） | ❌ | ❌ |
| 办理入住/退房 | ❌ | ✅ | ✅ |
| 押金操作 | ❌ | ✅ processDeposit | ✅ |
| 额外费用 | ❌ | ✅ updateExtraCharges | ✅ |

### 评价 / 消息 / 收益
| 操作 | 房客 | 房东 | 管理员 |
|---|---|---|---|
| 提交/修改评价 | ✅（限本人） | ❌ | ❌ |
| 回复评价 | ❌ | ✅ respondToReview | ❌ |
| 删除/隐藏评价 | ❌ | ❌ | ✅ |
| 发送消息 | ✅（会话双方） | ✅（会话双方） | ❌ |
| 查询收益 | ❌ | 👁（限本人） | 👁 |
| 结算收益 | — | — | 系统定时任务 |

## 三、Agent 工具边界（由矩阵推导）

### 第一层 FAQ Agent：只读工具集（零风险）
全部是现成的查询接口，不需要新写：
- `getOrderById` / `getMyOrders` —— 订单查询
- `getRefundPreview` —— 退款政策与金额预览
- `getCheckInCredential` / `getCheckInRecord` —— 入住凭证
- `getCheckOutRecord` —— 退房与押金状态
- `getReviewsByHomestay` / `getHomestayReviewStats` —— 评价
- `HomestayQueryService` 系列 —— 房源详情、设施、政策字段
- `PricingService` 报价查询 —— "周末多少钱"类问题

### 第二层订单服务 Agent：只开放申请型写操作
| 工具 | 说明 |
|---|---|
| `requestUserRefund` | 客人发起退款申请，房东/管理员审批——agent 代提申请 |
| `cancelOrderWithReason` | 客人取消订单，走既有规则 |
| `raiseDisputeByGuest`（新增） | 客人发起争议，agent 起草、客人确认、管理员仲裁 |
| `sendMessage`（起草模式） | agent 起草回复，房东一键确认后才发送，senderId 仍是房东 |

### Agent 绝对禁区（连申请权都没有）
- `approveRefund` / `rejectRefund` / `executeRefund` —— 退款审批
- `processDeposit` —— 押金四种操作
- `updateExtraCharges` —— 额外费用
- `confirmPayment` —— 支付确认
- `deleteOrder` / `batchDelete` —— 删除类
- `setReviewVisibility` —— 评价审核

**设计原则**：agent 的每个写操作都必须落在"申请→审批"轨道上，且审批人永远是人类。agent 回答涉及禁区时，固定话术是"这个需要房东/管理员处理，我已为你记录并通知"。

## 四、失败模式与护栏

| 失败模式 | 护栏 |
|---|---|
| Agent 幻觉出不存在的政策（如编造退款比例） | 所有金额/政策回答必须引用 getRefundPreview/定价规则的真实返回值，回答附来源字段；无法溯源时转人工 |
| Agent 被诱导执行越权操作（prompt injection） | 工具层白名单硬编码（上表），LLM 输出不直接映射到 service 调用，中间有权限校验层；任何 ADMIN 接口在工具注册表中不存在 |
| Agent 代发的消息造成纠纷 | 房东侧消息一律"起草+确认"模式，不自动发送 |
| Agent 循环处理同一问题 | 同一会话 3 轮未解决自动转人工，转人工走现有 IM 通道 |
| 争议被 agent 误起草 | raiseDisputeByGuest 必须客人显式确认后才提交，agent 只组装材料 |
| 敏感场景（安全、紧急） | 关键词命中（人身安全/受伤/火灾等）直接跳过 agent，展示紧急联系入口——对齐 Airbnb 的 safety 直达通道 |

## 五、与三层架构的对应

- 第一层 FAQ agent = 只读工具集，最先做，零风险
- 第二层订单服务 agent = 申请型写操作（含新增的 raiseDisputeByGuest）
- 第三层争议辅助 agent = 给管理员生成"裁决建议草稿"（订单时间线+聊天摘要+历史相似案例），DisputeRecord 是现成的少样本数据

---

## 六、落地记录（2026-08-09 实现完成，v1.0）

> 本节记录 v0.2 设计定稿后的实际实现与决策，标注与设计的对应/偏离。

### 第一层 FAQ Agent（已上线）

- 7 个只读工具白名单：`query_my_order` / `get_refund_preview` / `get_check_in_info` / `get_check_out_info` / `get_homestay_detail` / `get_review_stats` / `calculate_price`
- 两阶段 JSON 协议（决策轮 + 回答轮），`LlmClient` 封装 OpenAI 兼容 chat/completions，不依赖原生 function calling
- 三道护栏：敏感词直达人工 / 同会话 3 轮转人工 / LLM 失败兜底转人工
- 工具返回脱敏（maskOrder：不含 guestPhone/checkInCode/doorPassword/refundTransactionId）

### 第二层 订单服务 Agent（2026-08-09）

**实现（与 v0.2 完全对齐）：**

| v0.2 设计 | 实际落地 |
|---|---|
| 新增 `raiseDisputeByGuest` 接口 | `DisputeService.raiseDisputeByGuest(orderId, reason)` + `GuestDisputeController`（`POST /api/guest/disputes`，401/403/400/404 分级） |
| 触发条件放宽 | 非 `DISPUTE_PENDING` 且不在终态集合 `{REFUNDED, COMPLETED, CANCELLED, CANCELLED_BY_USER, CANCELLED_BY_HOST, CANCELLED_SYSTEM}` 即可发起（房客争议是"对已发生事实的申诉"，不被退款状态机锁死） |
| 房客权限 | 必须是订单客人（`order.getGuest().getId() == currentUser.getId()`），否则 `AccessDeniedException` |
| 3 个申请型写操作工具 | `request_user_refund` / `cancel_order_with_reason`（固定 `CANCELLED_BY_USER`）/ `raise_dispute_by_guest`，注册进 AgentToolRegistry（7→10 工具） |

**关键实现决策：起草模式（比 v0.2 更严）**

- 写工具**只起草不执行**：校验订单归属后返回 `pendingAction`（待确认提案：action/orderId/reason/summary），不调任何 service 写方法
- 新增 `POST /api/support/agent/confirm`：用户确认后才真正执行（调 requestUserRefund / cancelOrderWithReason / raiseDisputeByGuest）
- `OrderAccessGuard` 提升为 public 并新增 `requireGuestOrder`（必须是订单客人，防越权替他人操作）
- 前端 `SupportAgentDialog` 渲染"待确认操作"卡片（确认/取消，防重复点击），确认后追加执行结果消息
- 原因：LLM 可能误判用户意图（问政策 ≠ 要执行），直接执行风险不可控；对齐 v0.2 护栏"客人显式确认后才提交"

### 第三层 争议辅助 Agent（2026-08-09）

- `DisputeAdvisorService.generateAdvice(orderId)`：管理员仲裁前一键生成裁决建议草稿
- 数据组装：订单时间线（基础字段 + remark 状态流转 + 争议/退款字段）+ 聊天摘要（订单会话最近 50 条，时间正序）+ 历史相似案例（最近 10 条已解决争议，排除当前订单）
- `LlmClient` 生成建议（倾向 APPROVED/REJECTED + 理由 + 政策建议 + 注意事项，markdown），失败降级返回原始材料不 500
- `AdminDisputeAdvisorController`：`GET /api/admin/disputes/advisor/{orderId}/advice`，`@PreAuthorize("hasRole('ADMIN')")`
- **红线**：只生成建议，绝不自动仲裁（不调 resolveDispute、不改订单状态），审批权永远在管理员

### 测试覆盖（agent 模块）

| 测试类 | 覆盖 |
|---|---|
| `DisputeServiceImplTest`（13 用例） | 房东/房客发起争议、仲裁、权限、状态机 |
| `AgentToolRegistryTest`（9 用例） | 10 工具白名单、禁区工具不在注册表 |
| `AgentWriteToolsTest`（11 用例） | 3 写工具起草模式（verify 不执行）+ confirm 分发 + 越权拒绝 |
| `SupportAgentServiceImplTest`（9 用例） | 编排、护栏、pendingAction 透传 |
| `DisputeAdvisorServiceImplTest`（6 用例） | 生成建议全流程 + LLM 失败降级 |

全部通过（2026-08-09 实测：相关测试类 Failures: 0）。
