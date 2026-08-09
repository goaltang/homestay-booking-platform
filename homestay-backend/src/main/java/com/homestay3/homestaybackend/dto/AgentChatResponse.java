package com.homestay3.homestaybackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 客服 Agent 对话响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentChatResponse {

    /**
     * 回复文本
     */
    private String answer;

    /**
     * 是否转人工（敏感场景/多轮未解决/LLM失败兜底）
     */
    private boolean handoffToHuman;

    /**
     * 本轮使用的工具名（可空）
     */
    private String toolUsed;

    /**
     * 会话ID（请求未提供时由服务端生成）
     */
    private String conversationId;

    /**
     * 待确认操作（可空；非空时前端渲染确认卡片）
     */
    private AgentPendingAction pendingAction;
}
