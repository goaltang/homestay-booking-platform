package com.homestay3.homestaybackend.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.homestay3.homestaybackend.config.AgentProperties;
import com.homestay3.homestaybackend.dto.AgentChatRequest;
import com.homestay3.homestaybackend.dto.AgentChatResponse;
import com.homestay3.homestaybackend.entity.Homestay;
import com.homestay3.homestaybackend.entity.Order;
import com.homestay3.homestaybackend.entity.User;
import com.homestay3.homestaybackend.exception.AccessDeniedException;
import com.homestay3.homestaybackend.repository.OrderRepository;
import com.homestay3.homestaybackend.service.DisputeService;
import com.homestay3.homestaybackend.service.OrderService;
import com.homestay3.homestaybackend.service.agent.impl.SupportAgentServiceImpl;
import com.homestay3.homestaybackend.service.agent.tools.QueryMyOrderTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * SupportAgentServiceImpl 单元测试
 * mock LlmClient + mock 工具注册表，验证两阶段协议与各项护栏
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SupportAgentServiceImplTest {

    @Mock
    private LlmClient llmClient;

    @Mock
    private AgentToolRegistry toolRegistry;

    @Mock
    private OrderService orderService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private DisputeService disputeService;

    private SupportAgentServiceImpl service;

    @BeforeEach
    void setUp() {
        AgentProperties properties = new AgentProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        properties.setMaxToolHops(2);
        service = new SupportAgentServiceImpl(llmClient, toolRegistry, properties,
                new ObjectMapper(), orderRepository, orderService, disputeService);
        when(toolRegistry.toolSpecs()).thenReturn(List.of("- query_my_order: 查询我的订单"));
    }

    // ========== 两阶段协议 ==========

    @Test
    void needToolTrue_executesToolThenAnswers() {
        when(llmClient.chat(any()))
                .thenReturn("{\"need_tool\": true, \"tool\": \"query_my_order\", \"args\": {\"orderId\": 1}}")
                .thenReturn("{\"need_tool\": false}")
                .thenReturn("您的订单当前状态为已支付。");
        when(toolRegistry.execute(eq("query_my_order"), any(), eq("alice")))
                .thenReturn(Map.of("status", "PAID"));

        AgentChatRequest request = new AgentChatRequest();
        request.setQuestion("我的订单现在什么状态？");

        AgentChatResponse response = service.chat(request, "alice");

        assertEquals("您的订单当前状态为已支付。", response.getAnswer());
        assertEquals("query_my_order", response.getToolUsed());
        assertFalse(response.isHandoffToHuman());
        assertNotNull(response.getConversationId());
        verify(toolRegistry).execute(eq("query_my_order"), any(), eq("alice"));
        verify(llmClient, times(3)).chat(any());
    }

    @Test
    void invalidDecisionJson_degradesToDirectAnswer() {
        when(llmClient.chat(any()))
                .thenReturn("抱歉，这段内容我无法解析成JSON")
                .thenReturn("您好，请问有什么可以帮您？");

        AgentChatRequest request = new AgentChatRequest();
        request.setQuestion("你好");

        AgentChatResponse response = service.chat(request, "alice");

        assertEquals("您好，请问有什么可以帮您？", response.getAnswer());
        assertNull(response.getToolUsed());
        assertFalse(response.isHandoffToHuman());
        verify(toolRegistry, never()).execute(anyString(), any(), anyString());
        verify(llmClient, times(2)).chat(any());
    }

    // ========== 待确认操作透传 ==========

    @Test
    void pendingAction_passthrough() {
        when(llmClient.chat(any()))
                .thenReturn("{\"need_tool\": true, \"tool\": \"request_user_refund\", \"args\": {\"orderId\": 1, \"reason\": \"行程有变\"}}")
                .thenReturn("{\"need_tool\": false}")
                .thenReturn("已为您准备好退款申请，请确认。");
        when(toolRegistry.execute(eq("request_user_refund"), any(), eq("alice")))
                .thenReturn(Map.of("pendingAction", Map.of(
                        "action", "request_user_refund",
                        "orderId", 1L,
                        "reason", "行程有变",
                        "summary", "将为订单 ORDER202608080001 申请退款")));

        AgentChatRequest request = new AgentChatRequest();
        request.setQuestion("帮我申请退款");

        AgentChatResponse response = service.chat(request, "alice");

        assertEquals("request_user_refund", response.getToolUsed());
        assertNotNull(response.getPendingAction());
        assertEquals("request_user_refund", response.getPendingAction().getAction());
        assertEquals(1L, response.getPendingAction().getOrderId());
        assertEquals("行程有变", response.getPendingAction().getReason());
        assertEquals("将为订单 ORDER202608080001 申请退款", response.getPendingAction().getSummary());
        verify(toolRegistry).execute(eq("request_user_refund"), any(), eq("alice"));
    }

    // ========== 护栏：敏感词直达 ==========

    @Test
    void sensitiveQuestion_skipsLlmAndHandsOff() {
        AgentChatRequest request = new AgentChatRequest();
        request.setQuestion("房间里有人受伤了怎么办？");

        AgentChatResponse response = service.chat(request, "alice");

        assertTrue(response.isHandoffToHuman());
        assertTrue(response.getAnswer().contains("110"));
        verify(llmClient, never()).chat(any());
        verify(toolRegistry, never()).execute(anyString(), any(), anyString());
    }

    // ========== 护栏：LLM 失败兜底 ==========

    @Test
    void llmFailure_fallsBackToHandoff() {
        when(llmClient.chat(any())).thenThrow(new AgentLlmException("调用超时"));

        AgentChatRequest request = new AgentChatRequest();
        request.setQuestion("我的订单什么状态？");

        AgentChatResponse response = service.chat(request, "alice");

        assertTrue(response.isHandoffToHuman());
        assertNotNull(response.getAnswer());
        assertTrue(response.getAnswer().contains("人工客服"));
    }

    // ========== 护栏：工具异常不 500 ==========

    @Test
    void toolExecutionFailure_answersGracefully() {
        when(llmClient.chat(any()))
                .thenReturn("{\"need_tool\": true, \"tool\": \"query_my_order\", \"args\": {\"orderId\": 1}}")
                .thenReturn("{\"need_tool\": false}")
                .thenReturn("抱歉，订单查询暂时失败，请稍后再试。");
        when(toolRegistry.execute(anyString(), any(), anyString()))
                .thenThrow(new RuntimeException("数据库异常"));

        AgentChatRequest request = new AgentChatRequest();
        request.setQuestion("查一下我的订单");

        AgentChatResponse response = service.chat(request, "alice");

        assertEquals("抱歉，订单查询暂时失败，请稍后再试。", response.getAnswer());
        assertFalse(response.isHandoffToHuman());
        assertEquals("query_my_order", response.getToolUsed());
    }

    // ========== 护栏：3 轮未解决转人工 ==========

    @Test
    void fourthRoundInSameConversation_handsOffToHuman() {
        AtomicInteger callCount = new AtomicInteger();
        when(llmClient.chat(any())).thenAnswer(invocation ->
                callCount.incrementAndGet() % 2 == 1
                        ? "{\"need_tool\": false}"
                        : "这是AI的回答");

        for (int i = 1; i <= 3; i++) {
            AgentChatRequest request = new AgentChatRequest();
            request.setQuestion("问题 " + i);
            request.setConversationId("conv-001");
            AgentChatResponse response = service.chat(request, "alice");
            assertFalse(response.isHandoffToHuman(), "第 " + i + " 轮不应转人工");
        }

        AgentChatRequest fourth = new AgentChatRequest();
        fourth.setQuestion("问题 4");
        fourth.setConversationId("conv-001");
        AgentChatResponse response = service.chat(fourth, "alice");

        assertTrue(response.isHandoffToHuman());
        verify(llmClient, times(6)).chat(any());
    }

    // ========== 订单工具越权（真实工具 + mock 仓储） ==========

    @Test
    void orderToolDeniedWhenUsernameIsNotOrderGuest() {
        Order order = buildOrder("alice", "host1");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        QueryMyOrderTool tool = new QueryMyOrderTool(orderService, orderRepository);

        assertThrows(AccessDeniedException.class,
                () -> tool.execute(Map.of("orderId", 1), "mallory"));
    }

    // ========== 会话ID ==========

    @Test
    void conversationIdGeneratedWhenAbsent() {
        when(llmClient.chat(any()))
                .thenReturn("{\"need_tool\": false}")
                .thenReturn("您好！");

        AgentChatRequest request = new AgentChatRequest();
        request.setQuestion("在吗");

        AgentChatResponse response = service.chat(request, "alice");

        assertNotNull(response.getConversationId());
        assertFalse(response.getConversationId().isBlank());
    }

    // ==================== 辅助方法 ====================

    private Order buildOrder(String guestUsername, String hostUsername) {
        User guest = new User();
        guest.setId(1L);
        guest.setUsername(guestUsername);

        User host = new User();
        host.setId(2L);
        host.setUsername(hostUsername);

        Homestay homestay = new Homestay();
        homestay.setId(10L);
        homestay.setTitle("测试房源");
        homestay.setOwner(host);

        Order order = new Order();
        order.setId(1L);
        order.setOrderNumber("ORDER202608080001");
        order.setGuest(guest);
        order.setHomestay(homestay);
        order.setStatus("PAID");
        order.setCheckInDate(LocalDate.of(2026, 8, 10));
        order.setCheckOutDate(LocalDate.of(2026, 8, 12));
        order.setNights(2);
        order.setGuestCount(2);
        order.setTotalAmount(new BigDecimal("500.00"));
        return order;
    }
}
