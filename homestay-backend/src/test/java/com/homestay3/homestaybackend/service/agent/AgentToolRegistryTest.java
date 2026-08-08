package com.homestay3.homestaybackend.service.agent;

import com.homestay3.homestaybackend.dto.CheckInCredentialDTO;
import com.homestay3.homestaybackend.entity.Homestay;
import com.homestay3.homestaybackend.entity.Order;
import com.homestay3.homestaybackend.entity.User;
import com.homestay3.homestaybackend.exception.AccessDeniedException;
import com.homestay3.homestaybackend.repository.OrderRepository;
import com.homestay3.homestaybackend.service.CheckInService;
import com.homestay3.homestaybackend.service.CheckOutService;
import com.homestay3.homestaybackend.service.HomestayQueryService;
import com.homestay3.homestaybackend.service.OrderService;
import com.homestay3.homestaybackend.service.PricingService;
import com.homestay3.homestaybackend.service.ReviewService;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * AgentToolRegistry 单元测试
 * 验证工具白名单硬编码边界与订单越权防护
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentToolRegistryTest {

    @Mock
    private OrderService orderService;

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

    private AgentToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new AgentToolRegistry(orderService, checkInService, checkOutService,
                homestayQueryService, reviewService, pricingService, orderRepository);
    }

    // ========== 白名单边界 ==========

    @Test
    void whitelistContainsExactlySevenTools() {
        assertEquals(7, registry.size());
        assertEquals(7, registry.toolSpecs().size());
    }

    @Test
    void whitelistContainsAllExpectedReadOnlyTools() {
        List<String> specs = registry.toolSpecs();
        String joined = String.join("\n", specs);
        assertTrue(joined.contains("query_my_order"));
        assertTrue(joined.contains("get_refund_preview"));
        assertTrue(joined.contains("get_check_in_info"));
        assertTrue(joined.contains("get_check_out_info"));
        assertTrue(joined.contains("get_homestay_detail"));
        assertTrue(joined.contains("get_review_stats"));
        assertTrue(joined.contains("calculate_price"));
    }

    @Test
    void executeUnregisteredToolThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.execute("delete_order", Map.of(), "alice"));
    }

    @Test
    void executeForbiddenWriteToolThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.execute("approve_refund", Map.of("orderId", 1), "alice"));
        assertThrows(IllegalArgumentException.class,
                () -> registry.execute("processDeposit", Map.of(), "host1"));
    }

    // ========== 订单工具越权防护 ==========

    @Test
    void orderToolAllowsOrderGuest() {
        Order order = buildOrder("alice", "host1");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        Object result = registry.execute("query_my_order", Map.of("orderId", 1), "alice");

        assertNotNull(result);
        assertTrue(result instanceof Map);
    }

    @Test
    void orderToolAllowsOrderHost() {
        Order order = buildOrder("alice", "host1");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        Object result = registry.execute("query_my_order", Map.of("orderId", 1), "host1");

        assertNotNull(result);
    }

    @Test
    void orderToolDeniedForUnrelatedUser() {
        Order order = buildOrder("alice", "host1");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThrows(AccessDeniedException.class,
                () -> registry.execute("query_my_order", Map.of("orderId", 1), "mallory"));
    }

    @Test
    void refundPreviewDeniedForUnrelatedUser() {
        Order order = buildOrder("alice", "host1");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThrows(AccessDeniedException.class,
                () -> registry.execute("get_refund_preview", Map.of("orderId", 1), "mallory"));
    }

    @Test
    void checkInInfoDelegatesAfterAccessCheck() {
        Order order = buildOrder("alice", "host1");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        CheckInCredentialDTO credential = CheckInCredentialDTO.builder()
                .checkInCode("123456")
                .build();
        when(checkInService.getCheckInCredential(1L)).thenReturn(credential);

        Object result = registry.execute("get_check_in_info", Map.of("orderId", 1), "alice");

        assertSame(credential, result);
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
