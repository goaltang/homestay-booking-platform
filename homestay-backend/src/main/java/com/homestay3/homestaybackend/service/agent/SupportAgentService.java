package com.homestay3.homestaybackend.service.agent;

import com.homestay3.homestaybackend.dto.AgentChatRequest;
import com.homestay3.homestaybackend.dto.AgentChatResponse;

/**
 * AI 客服 Agent（第一层 FAQ，只读）编排服务
 */
public interface SupportAgentService {

    /**
     * 处理一轮用户咨询
     *
     * @param request  用户问题与上下文
     * @param username 当前登录用户名（JWT）
     * @return 回复（永不抛异常给前端，失败时兜底转人工）
     */
    AgentChatResponse chat(AgentChatRequest request, String username);
}
