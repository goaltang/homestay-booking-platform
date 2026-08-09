package com.homestay3.homestaybackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 争议裁决建议草稿结果
 * 管理员仲裁争议订单前的参考材料汇总（含 LLM 生成的裁决建议，仅供参考，最终由管理员决定）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisputeAdvisorResult {

    private Long orderId;          // 订单ID
    private String orderNumber;    // 订单号
    private String disputeReason;  // 争议原因
    private String timeline;       // 订单时间线（文本，来自 remark + 关键字段）
    private String chatSummary;    // 聊天记录摘要（文本；无会话则"无聊天记录"）
    private List<String> similarCases; // 历史相似案例（每项一条简短文本）
    private String suggestion;     // LLM 生成的裁决建议草稿（markdown 文本）
}
