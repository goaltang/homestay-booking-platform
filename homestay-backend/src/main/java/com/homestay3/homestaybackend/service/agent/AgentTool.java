package com.homestay3.homestaybackend.service.agent;

import java.util.Map;

/**
 * Agent 只读工具接口
 * 实现必须满足：
 * 1. 只做查询，不做任何写操作
 * 2. 涉及订单等私有数据时必须校验 username 是否为订单客人/房东，越权抛 AccessDeniedException
 * 3. 返回前对敏感字段脱敏
 */
public interface AgentTool {

    /**
     * 工具名（白名单内唯一）
     */
    String name();

    /**
     * 工具用途描述（供 LLM 决策用）
     */
    String description();

    /**
     * 参数说明：参数名 -> 说明（供 LLM 决策用）
     */
    Map<String, String> argsDescription();

    /**
     * 执行工具
     *
     * @param args     LLM 决策给出的参数
     * @param username 当前登录用户名（权限校验用）
     * @return 工具结果（将被序列化为 JSON 交给 LLM）
     */
    Object execute(Map<String, Object> args, String username);
}
