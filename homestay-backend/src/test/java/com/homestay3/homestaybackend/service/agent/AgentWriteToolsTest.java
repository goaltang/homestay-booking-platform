package com.homestay3.homestaybackend.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.homestay3.homestaybackend.config.AgentProperties;
import com.homestay3.homestaybackend.dto.AgentChatResponse;
import com.homestay3.homestaybackend.dto.AgentPendingAction;
import com.homestay3.homestaybackend.entity.Homestay;
import com.homestay3.homestaybackend.entity.Order;
import com.homestay3.homestaybackend.entity.User;
import com.homestay3.homestaybackend.exception.AccessDeniedException;
import com.homestay3.homestaybackend.repository.OrderRepository;
import com.homestay3.homestaybackend.service.CheckInService;
import com.homestay3.homestaybackend.service.CheckOutService;
import com.homestay3.homestaybackend.service.DisputeService;
import com.homestay3.homestaybackend.service.HomestayQueryService;
import com.homestay3.homestaybackend.service.OrderService;
import com.homestay3.homestaybackend.service.PricingService;
import com.homestay3.homestaybackend.service.ReviewService;
import com.homestay3.homestaybackend.service.agent.impl.SupportAgentServiceImpl;
import com.homestay3.homestaybackend.service.agent.tools.CancelOrderWithReasonTool;
import com.homestay3.homestaybackend.service.agent.tools.RaiseDisputeByGuestTool;
import com.homestay3.homestaybackend.service.agent.tools.RequestUserRefundTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Agent 第二层申请型写操作工具单元测试
 * 验证 request_user_refund / cancel_order_with_reason / raise_dispute_by_guest：
 * - 起草模式：只组装待确认提案（pendingAction），绝不直接调用 service 写方法
 * - 确认执行接口：confirmAction 才真正调用 service（仍带"必须是订单客人"校验）
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentWriteToolsTest {

    @Mock
    private OrderService orderService;

    @Mock
    private DisputeService disputeService;

    @Mock
    private CheckInService checkInService;

    @Mock
    private CheckOutService checkOutService;

    @Mock
    private HomestayQueryService homestayQueryService;

    @Mock
    private ReviewService reviewService;

    @Mock
    private PricingService pricingService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private LlmClient llmClient;

    @Mock
    private AgentToolRegistry toolRegistry;

    private RequestUserRefundTool requestUserRefundTool;
    private CancelOrderWithReasonTool cancelOrderWithReasonTool;
    private RaiseDisputeByGuestTool raiseDisputeByGuestTool;
    private SupportAgentServiceImpl supportAgentService;

    @BeforeEach
    void setUp() {
        requestUserRefundTool = new RequestUserRefundTool(orderRepository);
        cancelOrderWithReasonTool = new CancelOrderWithReasonTool(orderRepository);
        raiseDisputeByGuestTool = new RaiseDisputeByGuestTool(orderRepository);

        AgentProperties properties = new AgentProperties();
        properties.setEnabled(true);
        properties.setMaxToolHops(2);
        supportAgentService = new SupportAgentServiceImpl(llmClient, toolRegistry, properties,
                new ObjectMapper(), orderRepository, orderService, disputeService);
    }

    // ========== 起草模式：只组装提案，绝不直接执行 ==========

    @SuppressWarnings("unchecked")
    @Test
    void requestUserRefund_DraftsOnly() {
        Order order = buildOrder("alice", "host1");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        Object result = requestUserRefundTool.execute(Map.of("orderId", 1, "reason", "行程有变"), "alice");

        verify(orderService, never()).requestUserRefund(anyLong(), anyString());
        assertTrue(result instanceof Map);
        Map<String, Object> map = (Map<String, Object>) result;
        assertTrue(map.containsKey("pendingAction"));
        Map<String, Object> pending = (Map<String, Object>) map.get("pendingAction");
        assertEquals("request_user_refund", pending.get("action"));
        assertEquals(1L, pending.get("orderId"));
        assertEquals("行程有变", pending.get("reason"));
        assertEquals("将为订单 ORDER202608080001 申请退款", pending.get("summary"));
    }

    @Test
    void requestUserRefund_NotOwner() {
        Order order = buildOrder("alice", "host1");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThrows(AccessDeniedException.class,
                () -> requestUserRefundTool.execute(Map.of("orderId", 1, "reason", "行程有变"), "mallory"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void cancelOrderWithReason_DraftsOnly() {
        Order order = buildOrder("alice", "host1");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        Object result = cancelOrderWithReasonTool.execute(Map.of("orderId", 1, "reason", "不想住了"), "alice");

        verify(orderService, never()).cancelOrderWithReason(anyLong(), anyString(), anyString());
        Map<String, Object> map = (Map<String, Object>) result;
        Map<String, Object> pending = (Map<String, Object>) map.get("pendingAction");
        assertEquals("cancel_order_with_reason", pending.get("action"));
        assertEquals(1L, pending.get("orderId"));
        assertEquals("不想住了", pending.get("reason"));
        assertEquals("将取消订单 ORDER202608080001", pending.get("summary"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void raiseDisputeByGuest_DraftsOnly() {
        Order order = buildOrder("alice", "host1");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        Object result = raiseDisputeByGuestTool.execute(Map.of("orderId", 1, "reason", "入住体验严重不符"), "alice");

        verify(disputeService, never()).raiseDisputeByGuest(anyLong(), anyString());
        Map<String, Object> map = (Map<String, Object>) result;
        Map<String, Object> pending = (Map<String, Object>) map.get("pendingAction");
        assertEquals("raise_dispute_by_guest", pending.get("action"));
        assertEquals(1L, pending.get("orderId"));
        assertEquals("入住体验严重不符", pending.get("reason"));
        assertEquals("将为您发起争议（订单 ORDER202608080001）", pending.get("summary"));
    }

    // ========== 确认执行接口：confirmAction 才真正执行 ==========

    @Test
    void confirmAction_requestUserRefund_Success() {
        Order order = buildOrder("alice", "host1");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        AgentPendingAction pending = AgentPendingAction.builder()
                .action("request_user_refund")
                .orderId(1L)
                .reason("行程有变")
                .build();

        AgentChatResponse response = supportAgentService.confirmAction(pending, "alice");

        verify(orderService).requestUserRefund(1L, "行程有变");
        assertEquals("request_user_refund", response.getToolUsed());
        assertFalse(response.isHandoffToHuman());
        assertNotNull(response.getAnswer());
    }

    @Test
    void confirmAction_cancelOrder_Success() {
        Order order = buildOrder("alice", "host1");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        AgentPendingAction pending = AgentPendingAction.builder()
                .action("cancel_order_with_reason")
                .orderId(1L)
                .reason("不想住了")
                .build();

        AgentChatResponse response = supportAgentService.confirmAction(pending, "alice");

        verify(orderService).cancelOrderWithReason(1L, "CANCELLED_BY_USER", "不想住了");
        assertEquals("cancel_order_with_reason", response.getToolUsed());
        assertNotNull(response.getAnswer());
    }

    @Test
    void confirmAction_raiseDispute_Success() {
        Order order = buildOrder("alice", "host1");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        AgentPendingAction pending = AgentPendingAction.builder()
                .action("raise_dispute_by_guest")
                .orderId(1L)
                .reason("入住体验严重不符")
                .build();

        AgentChatResponse response = supportAgentService.confirmAction(pending, "alice");

        verify(disputeService).raiseDisputeByGuest(1L, "入住体验严重不符");
        assertEquals("raise_dispute_by_guest", response.getToolUsed());
        assertNotNull(response.getAnswer());
    }

    @Test
    void confirmAction_unsupportedAction() {
        AgentPendingAction pending = AgentPendingAction.builder()
                .action("execute_refund")
                .orderId(1L)
                .reason("测试")
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> supportAgentService.confirmAction(pending, "alice"));
    }

    @Test
    void confirmAction_deniedForNonGuest() {
        Order order = buildOrder("alice", "host1");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        AgentPendingAction pending = AgentPendingAction.builder()
                .action("request_user_refund")
                .orderId(1L)
                .reason("行程有变")
                .build();

        assertThrows(AccessDeniedException.class,
                () -> supportAgentService.confirmAction(pending, "host1"));
        verify(orderService, never()).requestUserRefund(anyLong(), anyString());
    }

    @Test
    void confirmAction_missingFields_throws() {
        AgentPendingAction pending = AgentPendingAction.builder()
                .action("request_user_refund")
                .orderId(1L)
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> supportAgentService.confirmAction(pending, "alice"));
    }

    // ========== 注册表白名单边界 ==========

    @Test
    void registry_contains_write_tools() {
        AgentToolRegistry registry = new AgentToolRegistry(orderService, checkInService, checkOutService,
                homestayQueryService, reviewService, pricingService, orderRepository);

        assertTrue(registry.contains("request_user_refund"));
        assertTrue(registry.contains("cancel_order_with_reason"));
        assertTrue(registry.contains("raise_dispute_by_guest"));

        assertFalse(registry.contains("approve_refund"));
        assertFalse(registry.contains("reject_refund"));
        assertFalse(registry.contains("execute_refund"));
        assertFalse(registry.contains("process_deposit"));
        assertFalse(registry.contains("confirm_payment"));
        assertFalse(registry.contains("delete_order"));
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
