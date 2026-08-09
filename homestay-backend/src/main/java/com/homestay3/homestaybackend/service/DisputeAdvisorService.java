package com.homestay3.homestaybackend.service;

import com.homestay3.homestaybackend.dto.DisputeAdvisorResult;

/**
 * 争议辅助服务
 * 管理员仲裁争议订单前，一键生成"裁决建议草稿"（订单时间线 + 聊天摘要 + 历史相似案例 + LLM 建议）
 * 本服务只生成建议，绝不自动仲裁，最终审批权永远在管理员
 */
public interface DisputeAdvisorService {

    /**
     * 生成裁决建议草稿
     *
     * @param orderId 争议中的订单ID
     * @return 裁决建议草稿
     */
    DisputeAdvisorResult generateAdvice(Long orderId);
}
