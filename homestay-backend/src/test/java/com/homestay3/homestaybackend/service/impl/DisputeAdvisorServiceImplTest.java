package com.homestay3.homestaybackend.service.impl;

import com.homestay3.homestaybackend.dto.DisputeAdvisorResult;
import com.homestay3.homestaybackend.dto.DisputeRecordDTO;
import com.homestay3.homestaybackend.dto.MessageDTO;
import com.homestay3.homestaybackend.entity.Conversation;
import com.homestay3.homestaybackend.entity.Homestay;
import com.homestay3.homestaybackend.entity.Order;
import com.homestay3.homestaybackend.entity.User;
import com.homestay3.homestaybackend.exception.ResourceNotFoundException;
import com.homestay3.homestaybackend.model.OrderStatus;
import com.homestay3.homestaybackend.model.PaymentStatus;
import com.homestay3.homestaybackend.repository.ConversationRepository;
import com.homestay3.homestaybackend.repository.OrderRepository;
import com.homestay3.homestaybackend.service.ChatService;
import com.homestay3.homestaybackend.service.DisputeRecordService;
import com.homestay3.homestaybackend.service.agent.AgentLlmException;
import com.homestay3.homestaybackend.service.agent.LlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DisputeAdvisorService 单元测试
 * 测试裁决建议草稿生成的业务逻辑
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DisputeAdvisorServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ChatService chatService;

    @Mock
    private DisputeRecordService disputeRecordService;

    @Mock
    private LlmClient llmClient;

    @InjectMocks
    private DisputeAdvisorServiceImpl disputeAdvisorService;

    private Order testOrder;
    private Homestay testHomestay;
    private Conversation testConversation;

    @BeforeEach
    void setUp() {
        User host = new User();
        host.setId(10L);
        host.setUsername("host_user");

        User guest = new User();
        guest.setId(20L);
        guest.setUsername("guest_user");

        testHomestay = new Homestay();
        testHomestay.setId(100L);
        testHomestay.setTitle("海边小院");
        testHomestay.setOwner(host);

        testOrder = new Order();
        testOrder.setId(1L);
        testOrder.setOrderNumber("ORDER202608090001");
        testOrder.setHomestay(testHomestay);
        testOrder.setGuest(guest);
        testOrder.setCheckInDate(LocalDate.of(2026, 8, 1));
        testOrder.setCheckOutDate(LocalDate.of(2026, 8, 3));
        testOrder.setNights(2);
        testOrder.setTotalAmount(new BigDecimal("600.00"));
        testOrder.setStatus(OrderStatus.DISPUTE_PENDING.name());
        testOrder.setPaymentStatus(PaymentStatus.DISPUTED);
        testOrder.setDisputeReason("房源与描述不符");
        testOrder.setRemark("争议发起 - 原因: 房源与描述不符");

        testConversation = Conversation.builder()
                .id(500L)
                .homestay(testHomestay)
                .host(host)
                .guest(guest)
                .build();
    }

    @Test
    void generateAdvice_OrderNotFound() {
        // given: 订单不存在
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        // when & then
        assertThrows(ResourceNotFoundException.class, () -> disputeAdvisorService.generateAdvice(99L));
    }

    @Test
    void generateAdvice_NotInDispute() {
        // given: 订单非 DISPUTE_PENDING
        testOrder.setStatus(OrderStatus.PAID.name());
        testOrder.setPaymentStatus(PaymentStatus.PAID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // when & then
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> disputeAdvisorService.generateAdvice(1L));
        assertEquals("订单不在争议中，无法生成裁决建议", exception.getMessage());
    }

    @Test
    void generateAdvice_Success_WithChatAndCases() {
        // given
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(conversationRepository.findByHomestayIdAndHostIdAndGuestId(100L, 10L, 20L))
                .thenReturn(Optional.of(testConversation));

        MessageDTO guestMsg = MessageDTO.builder()
                .senderId(20L).senderUsername("guest_user").content("你好，卫生间很脏，和描述不符")
                .build();
        MessageDTO hostMsg = MessageDTO.builder()
                .senderId(10L).senderUsername("host_user").content("抱歉，我马上安排清洁")
                .build();
        Page<MessageDTO> messages = new PageImpl<>(List.of(hostMsg, guestMsg));
        when(chatService.getMessages(eq(500L), isNull(), any(Pageable.class))).thenReturn(messages);

        DisputeRecordDTO similarRecord = DisputeRecordDTO.builder()
                .orderId(999L)
                .orderNumber("ORDER202607010001")
                .disputeReason("卫生问题")
                .resolution("APPROVED")
                .resolutionNote("核实属实，批准退款")
                .build();
        Page<DisputeRecordDTO> records = new PageImpl<>(List.of(similarRecord));
        when(disputeRecordService.getAllDisputeRecords(any(Pageable.class))).thenReturn(records);

        when(llmClient.chat(anyList())).thenReturn("## 裁决建议\n倾向 APPROVED\n理由：聊天记录及描述不符情况属实。");

        // when
        DisputeAdvisorResult result = disputeAdvisorService.generateAdvice(1L);

        // then
        assertNotNull(result);
        assertEquals(1L, result.getOrderId());
        assertEquals("ORDER202608090001", result.getOrderNumber());
        assertEquals("房源与描述不符", result.getDisputeReason());

        // timeline 含基础字段、remark、争议原因
        assertTrue(result.getTimeline().contains("订单号: ORDER202608090001"));
        assertTrue(result.getTimeline().contains("房源标题: 海边小院"));
        assertTrue(result.getTimeline().contains("客人: guest_user"));
        assertTrue(result.getTimeline().contains("总金额: 600.00"));
        assertTrue(result.getTimeline().contains("争议发起 - 原因: 房源与描述不符"));
        assertTrue(result.getTimeline().contains("争议原因: 房源与描述不符"));

        // chatSummary 含发送者: 内容（时间正序）
        assertTrue(result.getChatSummary().contains("guest_user: 你好，卫生间很脏，和描述不符"));
        assertTrue(result.getChatSummary().contains("host_user: 抱歉，我马上安排清洁"));

        // similarCases 含历史案例
        assertEquals(1, result.getSimilarCases().size());
        assertTrue(result.getSimilarCases().get(0).contains("ORDER202607010001"));
        assertTrue(result.getSimilarCases().get(0).contains("仲裁:APPROVED"));

        // suggestion 来自 LLM
        assertTrue(result.getSuggestion().startsWith("## 裁决建议"));
        assertTrue(result.getSuggestion().contains("倾向 APPROVED"));
        verify(llmClient).chat(anyList());
    }

    @Test
    void generateAdvice_LlmFails_Fallback() {
        // given
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(conversationRepository.findByHomestayIdAndHostIdAndGuestId(100L, 10L, 20L))
                .thenReturn(Optional.empty());
        when(disputeRecordService.getAllDisputeRecords(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(llmClient.chat(anyList())).thenThrow(new AgentLlmException("LLM 调用失败"));

        // when
        DisputeAdvisorResult result = disputeAdvisorService.generateAdvice(1L);

        // then: 降级返回原始材料，不抛异常
        assertNotNull(result);
        assertTrue(result.getSuggestion().startsWith("LLM 生成失败，请人工查看以下材料自行仲裁"));
        assertTrue(result.getSuggestion().contains("===== 订单时间线 ====="));
        assertTrue(result.getSuggestion().contains("===== 历史相似案例 ====="));
    }

    @Test
    void generateAdvice_NoChatNoCases() {
        // given: 无会话、无历史案例
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(conversationRepository.findByHomestayIdAndHostIdAndGuestId(100L, 10L, 20L))
                .thenReturn(Optional.empty());
        when(disputeRecordService.getAllDisputeRecords(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(llmClient.chat(anyList())).thenReturn("建议草稿");

        // when
        DisputeAdvisorResult result = disputeAdvisorService.generateAdvice(1L);

        // then
        assertNotNull(result);
        assertEquals("无聊天记录", result.getChatSummary());
        assertTrue(result.getSimilarCases().isEmpty());
        assertNotNull(result.getSuggestion());
        assertEquals("建议草稿", result.getSuggestion());
    }

    @Test
    void generateAdvice_SimilarCasesExcludeCurrent() {
        // given: 历史记录包含当前订单自身 + 一条其他已解决争议
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(conversationRepository.findByHomestayIdAndHostIdAndGuestId(100L, 10L, 20L))
                .thenReturn(Optional.empty());

        DisputeRecordDTO currentOrderRecord = DisputeRecordDTO.builder()
                .orderId(1L)
                .orderNumber("ORDER202608090001")
                .disputeReason("房源与描述不符")
                .resolution("APPROVED")
                .resolutionNote("当前订单的历史记录")
                .build();
        DisputeRecordDTO otherRecord = DisputeRecordDTO.builder()
                .orderId(999L)
                .orderNumber("ORDER202607010001")
                .disputeReason("卫生问题")
                .resolution("REJECTED")
                .resolutionNote("证据不足，拒绝退款")
                .build();
        Page<DisputeRecordDTO> records = new PageImpl<>(List.of(currentOrderRecord, otherRecord));
        when(disputeRecordService.getAllDisputeRecords(any(Pageable.class))).thenReturn(records);
        when(llmClient.chat(anyList())).thenReturn("建议草稿");

        // when
        DisputeAdvisorResult result = disputeAdvisorService.generateAdvice(1L);

        // then: 只保留其他订单的已解决争议
        assertEquals(1, result.getSimilarCases().size());
        assertFalse(result.getSimilarCases().get(0).contains("ORDER202608090001"));
        assertTrue(result.getSimilarCases().get(0).contains("ORDER202607010001"));
    }
}
