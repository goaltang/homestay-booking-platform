---
title: AI客服Agent-测试报告
date: 2026-08-09
tags:
  - homestay
  - agent
  - test
---
# AI 客服 Agent 测试报告（三层全量）

> 对应设计：[方案-AI客服Agent-三方权限矩阵.md](方案-AI客服Agent-三方权限矩阵.md)（v1.0）
> 实测日期：2026-08-09，全部 Failures: 0

## 一、后端单元测试（48 用例，全绿）

| 测试类 | 用例数 | 覆盖内容 |
|---|---|---|
| `DisputeServiceImplTest` | 13 | 房东/房客发起争议、仲裁 APPROVED/REJECTED、权限（非客人拒绝）、状态机校验（非退款中/重复争议/终态） |
| `AgentToolRegistryTest` | 9 | 10 工具白名单、工具数断言、toolSpecs 完整性 |
| `AgentWriteToolsTest` | 11 | 3 写工具**起草模式**（verify 不调用 service）、confirm 分发三分支、越权拒绝、非法 action、缺参校验、禁区工具不在注册表 |
| `SupportAgentServiceImplTest` | 9 | 两阶段编排、敏感词直达人工、3 轮转人工、LLM 失败兜底、pendingAction 透传 |
| `DisputeAdvisorServiceImplTest` | 6 | 生成建议全流程（时间线+聊天+相似案例）、订单不存在/非争议中、LLM 失败降级、无聊天无案例、排除当前订单 |

**运行方式**：
```bash
cd homestay-backend
mvn -o test -Dtest=DisputeServiceImplTest,AgentToolRegistryTest,AgentWriteToolsTest,SupportAgentServiceImplTest,DisputeAdvisorServiceImplTest
```

## 二、前端组件测试（5 用例，全绿）

`homestay-front/src/components/chat/__tests__/SupportAgentDialog.spec.ts`

| 用例 | 验证点 |
|---|---|
| 带 pendingAction 渲染确认卡片 | 卡片存在、含摘要、确认/取消按钮 |
| 无 pendingAction 不渲染卡片 | 普通问答不出现操作卡片 |
| 点击确认 | 调 confirmAgentAction（参数正确）、卡片消失、追加执行结果 |
| 点击取消 | 不调 confirm、卡片移除、提示"已取消该操作" |
| confirm 失败 | 卡片保留、ElMessage.error 提示 |

**运行方式**（WSL 注意）：
```bash
cd homestay-front
npx vitest run --pool=threads src/components/chat/__tests__/SupportAgentDialog.spec.ts
```
> ⚠️ WSL 下 vitest 默认 forks pool 会 worker 启动超时，必须加 `--pool=threads`。

## 三、关键验证结论

1. **起草模式生效**：3 个写工具只返回 pendingAction，`verify(...)` 确认未调用任何 service 写方法——"客人确认后才提交"护栏在测试层被强制
2. **越权防护**：非订单客人调用写工具/confirm 抛 AccessDeniedException
3. **禁区隔离**：approveRefund/rejectRefund/executeRefund/processDeposit/confirmPayment/deleteOrder 均不在注册表
4. **降级兜底**：LLM 失败时 agent 转人工、争议辅助返回原始材料，均不 500
5. **状态机安全**：争议只能在非终态订单发起，重复发起被拒

## 四、未覆盖项（已知）

- Controller 层测试（项目约定：以 service 层测试为主）
- 真实 LLM 联调（依赖 agent.llm.api-key 与外部 API，不在单测范围；联调方式见权限矩阵 v1.0）
- 前端 e2e（无 Playwright/Cypress 配置）
