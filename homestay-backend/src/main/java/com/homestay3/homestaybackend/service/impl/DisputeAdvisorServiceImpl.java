package com.homestay3.homestaybackend.service.impl;

import com.homestay3.homestaybackend.dto.DisputeAdvisorResult;
import com.homestay3.homestaybackend.dto.DisputeRecordDTO;
import com.homestay3.homestaybackend.dto.MessageDTO;
import com.homestay3.homestaybackend.entity.Conversation;
import com.homestay3.homestaybackend.entity.Order;
import com.homestay3.homestaybackend.exception.ResourceNotFoundException;
import com.homestay3.homestaybackend.model.OrderStatus;
import com.homestay3.homestaybackend.repository.ConversationRepository;
import com.homestay3.homestaybackend.repository.OrderRepository;
import com.homestay3.homestaybackend.service.ChatService;
import com.homestay3.homestaybackend.service.DisputeAdvisorService;
import com.homestay3.homestaybackend.service.DisputeRecordService;
import com.homestay3.homestaybackend.service.agent.AgentLlmException;
import com.homestay3.homestaybackend.service.agent.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 争议辅助服务实现
 * 组装订单时间线、聊天摘要、历史相似案例，交给 LLM 生成裁决建议草稿
 * 注意：只生成建议，绝不自动仲裁（不调 resolveDispute、不改订单状态）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DisputeAdvisorServiceImpl implements DisputeAdvisorService {

    private final OrderRepository orderRepository;
    private final ConversationRepository conversationRepository;
    private final ChatService chatService;
    private final DisputeRecordService disputeRecordService;
    private final LlmClient llmClient;

    private static final int CHAT_MESSAGE_LIMIT = 50;
    private static final int SIMILAR_CASE_LIMIT = 10;

    @Override
    @Transactional(readOnly = true)
    public DisputeAdvisorResult generateAdvice(Long orderId) {
        // 校验订单存在
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("订单不存在: " + orderId));

        // 校验订单在争议中
        if (!OrderStatus.DISPUTE_PENDING.name().equals(order.getStatus())) {
            throw new IllegalStateException("订单不在争议中，无法生成裁决建议");
        }

        String timeline = buildTimeline(order);
        String chatSummary = buildChatSummary(order);
        List<String> similarCases = buildSimilarCases(orderId);
        String suggestion = buildSuggestion(timeline, chatSummary, similarCases);

        return DisputeAdvisorResult.builder()
                .orderId(orderId)
                .orderNumber(order.getOrderNumber())
                .disputeReason(order.getDisputeReason())
                .timeline(timeline)
                .chatSummary(chatSummary)
                .similarCases(similarCases)
                .suggestion(suggestion)
                .build();
    }

    /**
     * 组装订单时间线：基础字段 + remark 状态流转 + 争议/退款相关字段
     */
    private String buildTimeline(Order order) {
        StringBuilder sb = new StringBuilder();
        sb.append("订单号: ").append(nullSafe(order.getOrderNumber())).append('\n');
        if (order.getHomestay() != null) {
            sb.append("房源标题: ").append(nullSafe(order.getHomestay().getTitle())).append('\n');
        }
        if (order.getGuest() != null) {
            sb.append("客人: ").append(nullSafe(order.getGuest().getUsername())).append('\n');
        }
        sb.append("入住日期: ").append(order.getCheckInDate()).append('\n');
        sb.append("退房日期: ").append(order.getCheckOutDate()).append('\n');
        sb.append("晚数: ").append(order.getNights()).append('\n');
        sb.append("总金额: ").append(order.getTotalAmount()).append('\n');
        sb.append("订单状态: ").append(nullSafe(order.getStatus())).append('\n');
        sb.append("支付状态: ").append(order.getPaymentStatus() != null ? order.getPaymentStatus().name() : "未知").append('\n');
        if (order.getRemark() != null && !order.getRemark().isBlank()) {
            sb.append("状态流转记录:\n").append(order.getRemark()).append('\n');
        }
        if (order.getDisputeReason() != null && !order.getDisputeReason().isBlank()) {
            sb.append("争议原因: ").append(order.getDisputeReason()).append('\n');
        }
        if (order.getRefundReason() != null && !order.getRefundReason().isBlank()) {
            sb.append("退款原因: ").append(order.getRefundReason()).append('\n');
        }
        if (order.getRefundRejectionReason() != null && !order.getRefundRejectionReason().isBlank()) {
            sb.append("退款拒绝原因: ").append(order.getRefundRejectionReason()).append('\n');
        }
        return sb.toString().trim();
    }

    /**
     * 组装聊天记录摘要
     * ChatServiceImpl.getMessages 的 userId 参数仅作权限预留，实现中并未使用，
     * 传 null 不会 NPE 也不会校验归属，故管理员视角可直接传 null 取最近 50 条
     */
    private String buildChatSummary(Order order) {
        if (order.getHomestay() == null || order.getHomestay().getOwner() == null || order.getGuest() == null) {
            return "无聊天记录";
        }
        Long homestayId = order.getHomestay().getId();
        Long hostId = order.getHomestay().getOwner().getId();
        Long guestId = order.getGuest().getId();

        Optional<Conversation> conversationOpt = conversationRepository
                .findByHomestayIdAndHostIdAndGuestId(homestayId, hostId, guestId);
        if (conversationOpt.isEmpty()) {
            return "无聊天记录";
        }

        Page<MessageDTO> messages = chatService.getMessages(
                conversationOpt.get().getId(), null, PageRequest.of(0, CHAT_MESSAGE_LIMIT));
        if (messages.isEmpty()) {
            return "无聊天记录";
        }

        // getMessages 按 createdAt 倒序返回（新在前），反转成时间正序更易读
        List<MessageDTO> reversed = new ArrayList<>(messages.getContent());
        java.util.Collections.reverse(reversed);

        StringBuilder sb = new StringBuilder();
        for (MessageDTO message : reversed) {
            sb.append(nullSafe(message.getSenderUsername())).append(": ")
                    .append(nullSafe(message.getContent())).append('\n');
        }
        return sb.toString().trim();
    }

    /**
     * 组装历史相似案例：最近已解决的争议（resolution 非空），排除当前订单自身
     */
    private List<String> buildSimilarCases(Long orderId) {
        Page<DisputeRecordDTO> records = disputeRecordService.getAllDisputeRecords(PageRequest.of(0, SIMILAR_CASE_LIMIT));
        List<String> cases = new ArrayList<>();
        for (DisputeRecordDTO record : records.getContent()) {
            if (record.getResolution() == null || record.getResolution().isBlank()) {
                continue;
            }
            if (record.getOrderId() != null && record.getOrderId().equals(orderId)) {
                continue;
            }
            cases.add(String.format("订单%s 争议原因:%s 仲裁:%s 备注:%s",
                    nullSafe(record.getOrderNumber()),
                    nullSafe(record.getDisputeReason()),
                    record.getResolution(),
                    nullSafe(record.getResolutionNote())));
        }
        return cases;
    }

    /**
     * 调用 LLM 生成裁决建议草稿
     * LLM 失败时降级为"材料 + 人工仲裁提示"，不让整个接口 500（对齐 agent 兜底风格）
     */
    private String buildSuggestion(String timeline, String chatSummary, List<String> similarCases) {
        StringBuilder material = new StringBuilder();
        material.append("===== 订单时间线 =====\n").append(timeline).append("\n\n");
        material.append("===== 聊天记录摘要 =====\n").append(chatSummary).append("\n\n");
        material.append("===== 历史相似案例 =====\n");
        if (similarCases.isEmpty()) {
            material.append("暂无历史案例\n");
        } else {
            for (String similarCase : similarCases) {
                material.append("- ").append(similarCase).append('\n');
            }
        }

        Map<String, String> systemMessage = new LinkedHashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", "你是民宿平台的争议仲裁助手。管理员会提供订单时间线、聊天记录摘要和历史相似案例，"
                + "请基于这些材料生成一份裁决建议草稿。输出要求：1) 明确给出倾向 APPROVED（批准退款）还是 REJECTED（拒绝退款）；"
                + "2) 给出理由；3) 如有可参考的平台政策给出建议；4) 列出注意事项。"
                + "请使用 markdown 格式、中文、简洁。注意：这只是建议草稿，最终由管理员决定。");

        Map<String, String> userMessage = new LinkedHashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", material.toString());

        try {
            return llmClient.chat(List.of(systemMessage, userMessage));
        } catch (AgentLlmException | IllegalStateException e) {
            log.warn("LLM 生成裁决建议失败，降级返回原始材料: {}", e.getMessage());
            return "LLM 生成失败，请人工查看以下材料自行仲裁\n\n" + material;
        }
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
