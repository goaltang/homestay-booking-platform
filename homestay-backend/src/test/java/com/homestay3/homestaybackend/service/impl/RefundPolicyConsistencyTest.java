package com.homestay3.homestaybackend.service.impl;

import com.homestay3.homestaybackend.dto.OrderDTO;
import com.homestay3.homestaybackend.dto.PricingResult;
import com.homestay3.homestaybackend.dto.refund.RefundRequest;
import com.homestay3.homestaybackend.dto.refund.RefundResponse;
import com.homestay3.homestaybackend.entity.Homestay;
import com.homestay3.homestaybackend.entity.Order;
import com.homestay3.homestaybackend.entity.PaymentRecord;
import com.homestay3.homestaybackend.entity.User;
import com.homestay3.homestaybackend.exception.AccessDeniedException;
import com.homestay3.homestaybackend.model.OrderStatus;
import com.homestay3.homestaybackend.model.PaymentStatus;
import com.homestay3.homestaybackend.model.RefundType;
import com.homestay3.homestaybackend.mq.OrderTimeoutProducer;
import com.homestay3.homestaybackend.repository.CouponTemplateRepository;
import com.homestay3.homestaybackend.repository.HomestayRepository;
import com.homestay3.homestaybackend.repository.OrderRepository;
import com.homestay3.homestaybackend.repository.PaymentRecordRepository;
import com.homestay3.homestaybackend.repository.PromotionUsageRepository;
import com.homestay3.homestaybackend.repository.ReviewRepository;
import com.homestay3.homestaybackend.repository.UserCouponRepository;
import com.homestay3.homestaybackend.repository.UserRepository;
import com.homestay3.homestaybackend.service.BookingConflictService;
import com.homestay3.homestaybackend.service.CouponAnalyticsService;
import com.homestay3.homestaybackend.service.CouponService;
import com.homestay3.homestaybackend.service.EarningService;
import com.homestay3.homestaybackend.service.OrderNotificationService;
import com.homestay3.homestaybackend.service.PaymentService;
import com.homestay3.homestaybackend.service.PricingService;
import com.homestay3.homestaybackend.service.PromotionMatchService;
import com.homestay3.homestaybackend.service.RefundPolicyCalculator;
import com.homestay3.homestaybackend.service.SystemConfigService;
import com.homestay3.homestaybackend.service.search.UserBehaviorTrackingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 调用真实退款规则和三个服务入口，仅隔离数据库、通知及优惠券等外部副作用。
 */
class RefundPolicyConsistencyTest {

    private static final LocalDateTime CHECK_IN = LocalDateTime.of(2027, 1, 10, 14, 0);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private OrderRepository orderRepository;
    private UserRepository userRepository;
    private PaymentRecordRepository paymentRecordRepository;
    private PricingService pricingService;
    private PaymentService paymentService;
    private OrderServiceImpl orderService;
    private PaymentProcessingServiceImpl paymentProcessingService;
    private OrderLifecycleServiceImpl orderLifecycleService;
    private User guest;
    private User host;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        userRepository = mock(UserRepository.class);
        paymentRecordRepository = mock(PaymentRecordRepository.class);
        pricingService = mock(PricingService.class);
        paymentService = mock(PaymentService.class);
        guest = user(1L, "guest", "ROLE_USER");
        host = user(2L, "host", "ROLE_HOST");
        authenticate(guest);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    static Stream<Arguments> refundCases() {
        return Stream.of(
                Arguments.of("宽松：恰好24小时全退实付", 1, 24 * 3600L, 2, "800.00", false, "800.00"),
                Arguments.of("宽松：不足24小时扣实付首晚", 1, 24 * 3600L - 1, 2, "800.00", false, "400.00"),
                Arguments.of("普通：恰好48小时全退实付", 2, 48 * 3600L, 2, "800.00", false, "800.00"),
                Arguments.of("普通：不足48小时退实付一半", 2, 48 * 3600L - 1, 2, "800.00", false, "400.00"),
                Arguments.of("普通：恰好24小时退实付一半", 2, 24 * 3600L, 3, "800.00", false, "400.00"),
                Arguments.of("普通：不足24小时扣实付首晚", 2, 24 * 3600L - 1, 3, "800.00", false, "533.33"),
                Arguments.of("严格：恰好72小时全退实付", 3, 72 * 3600L, 2, "800.00", false, "800.00"),
                Arguments.of("严格：不足72小时退实付一半", 3, 72 * 3600L - 1, 2, "800.00", false, "400.00"),
                Arguments.of("普通：入住后48小时不恢复全退", 2, -48 * 3600L, 2, "800.00", false, "400.00"),
                Arguments.of("普通：单晚不足24小时不退款", 2, 12 * 3600L, 1, "800.00", false, "0.00"),
                Arguments.of("宽松：三晚扣首晚保留分币精度", 1, 12 * 3600L, 3, "100.00", false, "66.67"),
                Arguments.of("普通：无快照回退订单总金额", 2, 36 * 3600L, 2, null, false, "500.00"),
                Arguments.of("普通：快照查询失败回退订单总金额", 2, 36 * 3600L, 2, null, true, "500.00")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refundCases")
    void previewRequestAndCancellationPersistTheSameRefund(String scenario, int policy,
            long secondsBeforeCheckIn, int nights, String payableAmount, boolean snapshotUnavailable,
            String expectedAmount) {
        arrangeServices(secondsBeforeCheckIn);
        arrangeSnapshot(payableAmount, snapshotUnavailable);
        Order previewOrder = paidOrder(101L, policy, nights);
        Order requestedOrder = paidOrder(102L, policy, nights);
        Order cancelledOrder = paidOrder(103L, policy, nights);
        arrangeOrder(previewOrder);
        arrangeOrder(requestedOrder);
        arrangeOrder(cancelledOrder);

        Map<String, Object> preview = orderService.getRefundPreview(previewOrder.getId());
        OrderDTO requested = paymentProcessingService.requestUserRefund(requestedOrder.getId(), "行程变更");
        OrderDTO cancelled = orderLifecycleService.cancelOrderWithReason(cancelledOrder.getId(),
                OrderStatus.CANCELLED_BY_USER.name(), "行程变更");

        assertEquals(true, preview.get("eligible"), scenario);
        assertMoney(expectedAmount, (BigDecimal) preview.get("estimatedRefundAmount"));
        assertMoney("1000.00", (BigDecimal) preview.get("totalAmount"));
        assertEquals(policy, preview.get("policyType"));
        assertEquals(previewOrder.getId(), preview.get("orderId"));
        assertEquals(previewOrder.getOrderNumber(), preview.get("orderNumber"));
        String description = assertInstanceOf(String.class, preview.get("policyDescription"));
        String policyName = policy == 1 ? "宽松政策" : policy == 3 ? "严格政策" : "普通政策";
        assertTrue(description.startsWith(policyName + "："));
        if (secondsBeforeCheckIn < 0) {
            assertTrue(description.contains("已过入住时间"));
        }
        assertEquals(secondsBeforeCheckIn / 3600L, preview.get("hoursBeforeCheckIn"));
        assertEquals(PaymentStatus.PAID, previewOrder.getPaymentStatus(), "预览不修改支付状态");
        assertEquals(OrderStatus.REFUND_PENDING.name(), requested.getStatus());
        assertEquals(OrderStatus.REFUND_PENDING.name(), cancelled.getStatus());
        assertMoney(expectedAmount, cancelled.getRefundAmount());

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, times(2)).save(saved.capture());
        assertEquals(requestedOrder, saved.getAllValues().get(0));
        assertEquals(cancelledOrder, saved.getAllValues().get(1));
        for (Order order : saved.getAllValues()) {
            assertMoney(expectedAmount, order.getRefundAmount());
            assertEquals(OrderStatus.REFUND_PENDING.name(), order.getStatus());
            assertEquals(PaymentStatus.REFUND_PENDING, order.getPaymentStatus());
            assertEquals(RefundType.USER_REQUESTED, order.getRefundType());
        }
        verify(paymentService, never()).processRefund(any());
    }

    @Test
    void adminDirectRefundUsesDiscountedPayableAmount() {
        arrangeServices(36 * 3600L);
        arrangeSnapshot("800.00", false);
        authenticate(user(3L, "admin", "ROLE_ADMIN"));
        Order order = paidOrder(104L, 2, 2);
        arrangeOrder(order);
        when(paymentRecordRepository.findTopByOrderIdAndStatusOrderByCreatedAtDesc(order.getId(), "SUCCESS"))
                .thenReturn(Optional.empty());

        paymentProcessingService.executeRefund(order.getId(), "管理员退款");

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(saved.capture());
        assertMoney("400.00", saved.getValue().getRefundAmount());
        assertEquals(OrderStatus.REFUNDED.name(), saved.getValue().getStatus());
        assertEquals(PaymentStatus.REFUNDED, saved.getValue().getPaymentStatus());
        assertEquals(RefundType.ADMIN_INITIATED, saved.getValue().getRefundType());
        verify(paymentService, never()).processRefund(any());
    }

    @Test
    void approvalKeepsTheRequestedAmountInsteadOfRecalculating() {
        arrangeServices(36 * 3600L);
        authenticate(user(3L, "admin", "ROLE_ADMIN"));
        Order order = paidOrder(105L, 2, 2);
        new OrderStatusUpdater().markRefundPending(order);
        order.setRefundAmount(new BigDecimal("123.45"));
        order.setRefundType(RefundType.USER_REQUESTED);
        order.setRefundReason("行程变更");
        arrangeOrder(order);
        arrangeGatewayRefund(order, "APPROVED-REFUND");

        paymentProcessingService.approveRefund(order.getId(), "同意原申请金额");

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(saved.capture());
        assertMoney("123.45", saved.getValue().getRefundAmount());
        assertEquals(OrderStatus.REFUNDED.name(), saved.getValue().getStatus());
        assertEquals(PaymentStatus.REFUNDED, saved.getValue().getPaymentStatus());
        assertEquals("APPROVED-REFUND", saved.getValue().getRefundTransactionId());
        ArgumentCaptor<RefundRequest> request = ArgumentCaptor.forClass(RefundRequest.class);
        verify(paymentService).processRefund(request.capture());
        assertEquals(order.getId(), request.getValue().getOrderId());
        assertMoney("123.45", request.getValue().getRefundAmount());
        assertEquals(RefundType.USER_REQUESTED, request.getValue().getRefundType());
        assertEquals("行程变更", request.getValue().getRefundReason());
        verifyNoInteractions(pricingService);
    }

    @Test
    void directRefundSendsCalculatedPayableAmountToPaymentGateway() {
        arrangeServices(36 * 3600L);
        arrangeSnapshot("800.00", false);
        authenticate(user(3L, "admin", "ROLE_ADMIN"));
        Order order = paidOrder(109L, 2, 2);
        arrangeOrder(order);
        arrangeGatewayRefund(order, "DIRECT-REFUND");

        paymentProcessingService.executeRefund(order.getId(), "管理员退款");

        ArgumentCaptor<RefundRequest> request = ArgumentCaptor.forClass(RefundRequest.class);
        verify(paymentService).processRefund(request.capture());
        assertEquals(order.getId(), request.getValue().getOrderId());
        assertMoney("400.00", request.getValue().getRefundAmount());
        assertEquals(RefundType.ADMIN_INITIATED, request.getValue().getRefundType());
        assertEquals("管理员退款", request.getValue().getRefundReason());
        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(saved.capture());
        assertMoney("400.00", saved.getValue().getRefundAmount());
        assertEquals(OrderStatus.REFUNDED.name(), saved.getValue().getStatus());
        assertEquals(PaymentStatus.REFUNDED, saved.getValue().getPaymentStatus());
        assertEquals("DIRECT-REFUND", saved.getValue().getRefundTransactionId());
    }

    @Test
    void unpaidOrderDoesNotReadPricingForPreviewOrRefundRequest() {
        arrangeServices(36 * 3600L);
        Order order = paidOrder(106L, 2, 2);
        new OrderStatusUpdater().markConfirmed(order);
        arrangeOrder(order);

        Map<String, Object> preview = orderService.getRefundPreview(order.getId());

        assertEquals(false, preview.get("eligible"));
        assertThrows(IllegalStateException.class,
                () -> paymentProcessingService.requestUserRefund(order.getId(), "未支付退款"));
        assertEquals(PaymentStatus.UNPAID, order.getPaymentStatus());
        verifyNoInteractions(pricingService);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void unrelatedGuestCannotReadRefundPreviewOrPricing() {
        arrangeServices(36 * 3600L);
        Order order = paidOrder(107L, 2, 2);
        arrangeOrder(order);
        authenticate(user(99L, "outsider", "ROLE_USER"));

        assertThrows(AccessDeniedException.class, () -> orderService.getRefundPreview(order.getId()));

        verifyNoInteractions(pricingService);
        verify(orderRepository, never()).save(any());
    }

    @ParameterizedTest(name = "缺少入住日期={0}时保留原有预览字段")
    @ValueSource(booleans = {true, false})
    void incompleteOrderPreviewKeepsMissingMetadataContract(boolean missingCheckInDate) {
        arrangeServices(36 * 3600L);
        Order order = paidOrder(108L, 1, 2);
        if (missingCheckInDate) {
            order.setCheckInDate(null);
        } else {
            order.setTotalAmount(null);
        }
        arrangeOrder(order);

        Map<String, Object> preview = orderService.getRefundPreview(order.getId());

        assertEquals(Set.of("estimatedRefundAmount", "policyDescription", "eligible", "orderId", "orderNumber"),
                preview.keySet());
        assertMoney("0.00", (BigDecimal) preview.get("estimatedRefundAmount"));
        assertEquals("无法计算退款金额（缺少入住日期或订单金额）", preview.get("policyDescription"));
        assertEquals(true, preview.get("eligible"));
        assertEquals(order.getId(), preview.get("orderId"));
        assertEquals(order.getOrderNumber(), preview.get("orderNumber"));
        verifyNoInteractions(pricingService);
        verify(orderRepository, never()).save(any());
    }

    @SuppressWarnings("unchecked")
    private void arrangeServices(long secondsBeforeCheckIn) {
        Clock clock = Clock.fixed(CHECK_IN.minusSeconds(secondsBeforeCheckIn).atZone(ZONE).toInstant(), ZONE);
        RefundPolicyCalculator calculator = new RefundPolicyCalculator(pricingService, clock);
        OrderStatusUpdater statusUpdater = new OrderStatusUpdater();
        HomestayRepository homestayRepository = mock(HomestayRepository.class);
        ReviewRepository reviewRepository = mock(ReviewRepository.class);
        OrderNotificationService notifications = mock(OrderNotificationService.class);
        EarningService earningService = mock(EarningService.class);
        CouponService couponService = mock(CouponService.class);
        PromotionUsageRepository promotionUsages = mock(PromotionUsageRepository.class);
        UserCouponRepository userCoupons = mock(UserCouponRepository.class);
        CouponAnalyticsService couponAnalytics = mock(CouponAnalyticsService.class);
        ObjectProvider<SystemConfigService> configProvider = mock(ObjectProvider.class);
        SystemConfigService config = mock(SystemConfigService.class);
        when(configProvider.getObject()).thenReturn(config);
        when(config.getConfigValue(anyString(), anyString())).thenAnswer(call -> call.getArgument(1));
        when(orderRepository.save(any(Order.class))).thenAnswer(call -> call.getArgument(0));
        when(promotionUsages.findByOrderId(anyLong())).thenReturn(Collections.emptyList());
        OrderDtoAssembler assembler = new OrderDtoAssembler(reviewRepository, userRepository, configProvider);

        paymentProcessingService = new PaymentProcessingServiceImpl(orderRepository, userRepository,
                paymentRecordRepository, earningService, notifications, paymentService, calculator,
                couponService, promotionUsages, userCoupons, couponAnalytics, assembler, statusUpdater);
        ObjectProvider<OrderTimeoutProducer> timeouts = mock(ObjectProvider.class);
        orderLifecycleService = new OrderLifecycleServiceImpl(orderRepository, userRepository,
                homestayRepository, notifications, earningService, assembler,
                mock(BookingConflictService.class), pricingService, calculator, couponService,
                mock(PromotionMatchService.class), promotionUsages, userCoupons,
                mock(CouponTemplateRepository.class), couponAnalytics,
                mock(UserBehaviorTrackingService.class), paymentProcessingService, statusUpdater, timeouts);
        orderService = new OrderServiceImpl(orderRepository, userRepository, homestayRepository,
                assembler, paymentProcessingService, orderLifecycleService, pricingService,
                calculator, configProvider);
    }

    private void arrangeSnapshot(String payableAmount, boolean unavailable) {
        if (unavailable) {
            when(pricingService.getPriceSnapshot(anyLong())).thenThrow(new IllegalStateException("快照暂不可用"));
        } else {
            PricingResult snapshot = payableAmount == null ? null
                    : PricingResult.builder().payableAmount(new BigDecimal(payableAmount)).build();
            when(pricingService.getPriceSnapshot(anyLong())).thenReturn(snapshot);
        }
    }

    private void arrangeOrder(Order order) {
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
    }

    private void arrangeGatewayRefund(Order order, String transactionId) {
        PaymentRecord record = new PaymentRecord();
        record.setOrderId(order.getId());
        record.setStatus("SUCCESS");
        record.setNeedRealRefund(true);
        when(paymentRecordRepository.findTopByOrderIdAndStatusOrderByCreatedAtDesc(order.getId(), "SUCCESS"))
                .thenReturn(Optional.of(record));
        when(paymentService.processRefund(any(RefundRequest.class)))
                .thenReturn(RefundResponse.builder().success(true).refundTradeNo(transactionId).build());
    }

    private void authenticate(User user) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getUsername(), null, Collections.emptyList()));
        SecurityContextHolder.setContext(context);
        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));
    }

    private Order paidOrder(Long id, int policy, int nights) {
        Homestay homestay = new Homestay();
        homestay.setId(10L);
        homestay.setTitle("测试民宿");
        homestay.setOwner(host);
        homestay.setCancelPolicyType(policy);
        return Order.builder()
                .id(id)
                .orderNumber("REFUND-" + id)
                .homestay(homestay)
                .guest(guest)
                .checkInDate(CHECK_IN.toLocalDate())
                .checkOutDate(CHECK_IN.toLocalDate().plusDays(nights))
                .nights(nights)
                .price(new BigDecimal("500.00"))
                .totalAmount(new BigDecimal("1000.00"))
                .status(OrderStatus.PAID.name())
                .paymentStatus(PaymentStatus.PAID)
                .build();
    }

    private static User user(Long id, String username, String role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        return user;
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "退款金额应为 " + expected);
    }
}
