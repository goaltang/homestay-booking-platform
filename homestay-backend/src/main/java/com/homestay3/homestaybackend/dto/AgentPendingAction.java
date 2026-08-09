package com.homestay3.homestaybackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 客服 Agent 待确认操作（申请型写操作起草结果）
 * agent 只起草不执行，前端据此渲染确认卡片；用户确认后由 /api/support/agent/confirm 真正执行。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentPendingAction {

    /**
     * 操作类型：request_user_refund | cancel_order_with_reason | raise_dispute_by_guest
     */
    private String action;

    /**
     * 目标订单ID
     */
    private Long orderId;

    /**
     * 操作原因
     */
    private String reason;

    /**
     * 给人看的摘要，如"将为订单 HK20260809xxx 申请退款"
     */
    private String summary;
}
