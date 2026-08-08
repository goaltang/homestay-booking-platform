package com.homestay3.homestaybackend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 客服 Agent 对话请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentChatRequest {

    @NotBlank(message = "问题不能为空")
    private String question;

    /**
     * 会话ID（可选，为空时服务端生成；用于多轮计数与转人工判断）
     */
    private String conversationId;

    /**
     * 上下文提示：用户当前正在查看的订单ID（可选）
     */
    private Long orderId;

    /**
     * 上下文提示：用户当前正在查看的房源ID（可选）
     */
    private Long homestayId;
}
