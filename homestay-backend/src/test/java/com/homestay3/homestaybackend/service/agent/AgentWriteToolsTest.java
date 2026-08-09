package com.homestay3.homestaybackend.service.agent;

import com.homestay3.homestaybackend.dto.OrderDTO;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Agent 第二层申请型写操作工具单元测试
 * 验证 request_user_refund / cancel_order_with_reason / raise_dispute_by_guest
 * 的订单归属校验、service 调用与注册表白名单边界
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

    private RequestUserRefundTool requestUserRefundTool;
    private CancelOrderWithReasonTool cancelOrderWithReasonTool;
    private RaiseDisputeByGuestTool raiseDisputeByGuestTool;

    @BeforeEach
    void setUp() {
        requestUserRefundTool = new RequestUserRefundTool(orderService, orderRepository);
        cancelOrderWithReasonTool = new CancelOrderWithReasonTool(orderService, orderRepository);
        raiseDisputeByGuestTool = new RaiseDisputeByGuestTool(disputeService, orderRepository);
    }

    @Test
    void requestUserRefund_Success() {
        Order order = buildOrder("alice", "host1");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        OrderDTO dto = OrderDTO.builder()
                .orderNumber("ORDER202608080001")
                .status("REFUND_PENDING")
                .paymentStatus("PAID")
                .refundAmount(new BigDecimal("500.00"))
                .refundReason("行程有变")
                .build();
        when(orderService.requestUserRefund(1L, "行程有变")).thenReturn(dto);

        Object result = requestUserRefundTool.execute(Map.of("orderId", 1, "reason", "行程有变"), "alice");

        verify(orderService).requestUserRefund(1L, "行程有变");
        assertTrue(result instanceof Map);
        Map<?, ?> map = (Map<?, ?>) result;
        assertEquals("ORDER202608080001", map.get("orderNumber"));
        assertEquals("REFUND_PENDING", map.get("status"));
        assertEquals("PAID", map.get("paymentStatus"));
        assertEquals(new BigDecimal("500.00"), map.get("refundAmount"));
        assertEquals("行程有变", map.get("refundReason"));
    }

    @Test
    void requestUserRefund_NotOwner() {
        Order order = buildOrder("alice", "host1");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThrows(AccessDeniedException.class,
                () -> requestUserRefundTool.execute(Map.of("orderId", 1, "reason", "行程有变"), "mallory"));
        verifyNoInteractions(orderService);
    }

    @Test
    void cancelOrderWithReason_Success() {
        Order order = buildOrder("alice", "host1");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        OrderDTO dto = OrderDTO.builder()
                .orderNumber("ORDER202608080001")
                .status("CANCELLED_BY_USER")
                .build();
        when(orderService.cancelOrderWithReason(1L, "CANCELLED_BY_USER", "不想住了")).thenReturn(dto);

        Object result = cancelOrderWithReasonTool.execute(Map.of("orderId", 1, "reason", "不想住了"), "alice");

        verify(orderService).cancelOrderWithReason(1L, "CANCELLED_BY_USER", "不想住了");
        assertTrue(result instanceof Map);
        Map<?, ?> map = (Map<?, ?>) result;
        assertEquals("ORDER202608080001", map.get("orderNumber"));
        assertEquals("CANCELLED_BY_USER", map.get("status"));
    }

    @Test
    void raiseDisputeByGuest_Success() {
        Order order = buildOrder("alice", "host1");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        OrderDTO dto = OrderDTO.builder()
                .orderNumber("ORDER202608080001")
                .status("DISPUTE_PENDING")
                .disputeReason("入住体验严重不符")
                .build();
        when(disputeService.raiseDisputeByGuest(1L, "入住体验严重不符")).thenReturn(dto);

        Object result = raiseDisputeByGuestTool.execute(Map.of("orderId", 1, "reason", "入住体验严重不符"), "alice");

        verify(disputeService).raiseDisputeByGuest(1L, "入住体验严重不符");
        assertTrue(result instanceof Map);
        Map<?, ?> map = (Map<?, ?>) result;
        assertEquals("ORDER202608080001", map.get("orderNumber"));
        assertEquals("DISPUTE_PENDING", map.get("status"));
        assertEquals("入住体验严重不符", map.get("disputeReason"));
    }

    @Test
    void registry_contains_write_tools() {
        AgentToolRegistry registry = new AgentToolRegistry(orderService, checkInService, checkOutService,
                homestayQueryService, reviewService, pricingService, disputeService, orderRepository);

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
