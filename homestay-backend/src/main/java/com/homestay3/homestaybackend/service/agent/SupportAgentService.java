package com.homestay3.homestaybackend.service.agent;

import com.homestay3.homestaybackend.dto.AgentChatRequest;
import com.homestay3.homestaybackend.dto.AgentChatResponse;
import com.homestay3.homestaybackend.dto.AgentPendingAction;

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

    /**
     * 确认执行待确认操作（申请型写操作的真正执行点，权限矩阵：审批权永远在人）
     * 校验 action/orderId/reason 非空、订单必须属于当前客人，再按 action 分发到对应 service 写方法。
     *
     * @param pending  待确认操作（来自 chat 响应中的 pendingAction）
     * @param username 当前登录用户名（JWT）
     * @return 执行结果
     * @throws IllegalArgumentException 参数缺失或不支持的操作（400）
     * @throws AccessDeniedException    当前用户不是订单客人（403）
     * @throws ResourceNotFoundException 订单不存在（404）
     */
    AgentChatResponse confirmAction(AgentPendingAction pending, String username);
}
