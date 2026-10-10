package com.homestay3.homestaybackend.service.impl;

import com.homestay3.homestaybackend.dto.OrderDTO;
import com.homestay3.homestaybackend.dto.refund.RefundRequest;
import com.homestay3.homestaybackend.dto.refund.RefundResponse;
import com.homestay3.homestaybackend.entity.User;
import com.homestay3.homestaybackend.exception.AccessDeniedException;
import com.homestay3.homestaybackend.exception.ResourceNotFoundException;
import com.homestay3.homestaybackend.entity.Homestay;
import com.homestay3.homestaybackend.model.HomestayStatus;
import com.homestay3.homestaybackend.entity.Order;
import com.homestay3.homestaybackend.model.OrderStatus;
import com.homestay3.homestaybackend.model.PaymentStatus;
import com.homestay3.homestaybackend.model.RefundType;
import com.homestay3.homestaybackend.repository.HomestayRepository;
import com.homestay3.homestaybackend.repository.OrderRepository;
import com.homestay3.homestaybackend.repository.ReviewRepository;
import com.homestay3.homestaybackend.repository.UserRepository;
import com.homestay3.homestaybackend.service.OrderLifecycleService;
import com.homestay3.homestaybackend.service.PaymentProcessingService;
import com.homestay3.homestaybackend.service.PricingService;
import com.homestay3.homestaybackend.service.RefundPolicyCalculator;
import com.homestay3.homestaybackend.service.SystemConfigService;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * OrderService 单元测试
 * 测试订单核心业务逻辑
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private HomestayRepository homestayRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private OrderLifecycleService orderLifecycleService;

    @Mock
    private PaymentProcessingService paymentProcessingService;

    @Mock
    private PricingService pricingService;

    @Mock
    private ObjectProvider<SystemConfigService> systemConfigServiceProvider;

    private OrderServiceImpl orderService;

    private User currentUser;
    private User hostUser;
    private Homestay homestay;
    private Order order;
    private OrderDTO orderDTO;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(orderRepository, userRepository, homestayRepository,
                new OrderDtoAssembler(reviewRepository, userRepository, systemConfigServiceProvider),
                paymentProcessingService, orderLifecycleService, pricingService,
                new RefundPolicyCalculator(pricingService, Clock.systemDefaultZone()), systemConfigServiceProvider);

        // 设置当前用户
        currentUser = new User();
        currentUser.setId(1L);
        currentUser.setUsername("testuser");
        currentUser.setRole("ROLE_USER");

        // 设置房东用户
        hostUser = new User();
        hostUser.setId(2L);
        hostUser.setUsername("hostuser");
        hostUser.setRole("ROLE_HOST");

        // 设置房源
        homestay = new Homestay();
        homestay.setId(1L);
        homestay.setTitle("测试民宿");
        homestay.setPrice(new BigDecimal("200"));
        homestay.setStatus(HomestayStatus.ACTIVE);
        homestay.setMinNights(1);
        homestay.setOwner(hostUser);
        homestay.setAutoConfirm(false);

        // 设置订单
        order = new Order();
        order.setId(1L);
        order.setOrderNumber("ORD202401010001");
        order.setHomestay(homestay);
        order.setGuest(currentUser);
        order.setGuestPhone("13800138000");
        order.setCheckInDate(LocalDate.now().plusDays(1));
        order.setCheckOutDate(LocalDate.now().plusDays(3));
        order.setNights(2);
        order.setGuestCount(2);
        order.setPrice(new BigDecimal("200"));
        order.setTotalAmount(new BigDecimal("400"));
        order.setStatus(OrderStatus.PENDING.name());
        order.setPaymentStatus(PaymentStatus.UNPAID);

        // 设置订单DTO
        orderDTO = OrderDTO.builder()
                .homestayId(1L)
                .guestPhone("13800138000")
                .checkInDate(LocalDate.now().plusDays(1))
                .checkOutDate(LocalDate.now().plusDays(3))
                .guestCount(2)
                .build();

        SystemConfigService mockSystemConfigService = mock(SystemConfigService.class);
        when(mockSystemConfigService.getConfigValue(eq("pricing.cleaning_fee"), any())).thenReturn("0.1");
        when(mockSystemConfigService.getConfigValue(eq("pricing.service_fee"), any())).thenReturn("0.15");
        when(systemConfigServiceProvider.getObject()).thenReturn(mockSystemConfigService);
    }

    private void mockSecurityContext() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(currentUser.getUsername());

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByUsername(currentUser.getUsername())).thenReturn(Optional.of(currentUser));
    }

    @Test
    void getOrderById_Success() {
        // 准备
        mockSecurityContext();
        when(orderLifecycleService.getOrderById(1L)).thenReturn(OrderDTO.builder().id(1L).orderNumber("ORD202401010001").build());

        // 执行
        OrderDTO result = orderService.getOrderById(1L);

        // 验证
        assertNotNull(result);
        verify(orderLifecycleService, times(1)).getOrderById(1L);
    }

    @Test
    void getOrderById_OrderNotFound() {
        // 准备
        when(orderLifecycleService.getOrderById(999L)).thenThrow(new ResourceNotFoundException("订单不存在"));

        // 执行和验证
        assertThrows(ResourceNotFoundException.class, () -> {
            orderService.getOrderById(999L);
        });
    }

    @Test
    void getOrderByOrderNumber_Success() {
        // 准备
        mockSecurityContext();
        when(orderLifecycleService.getOrderByOrderNumber("ORD202401010001")).thenReturn(OrderDTO.builder().id(1L).orderNumber("ORD202401010001").build());

        // 执行
        OrderDTO result = orderService.getOrderByOrderNumber("ORD202401010001");

        // 验证
        assertNotNull(result);
        verify(orderLifecycleService, times(1)).getOrderByOrderNumber("ORD202401010001");
    }

    @Test
    void updateOrderStatus_Success_Confirmed() {
        // 准备
        mockSecurityContext();
        when(orderLifecycleService.updateOrderStatus(1L, OrderStatus.CONFIRMED.name())).thenReturn(OrderDTO.builder().id(1L).status(OrderStatus.CONFIRMED.name()).build());

        // 执行
        OrderDTO result = orderService.updateOrderStatus(1L, OrderStatus.CONFIRMED.name());

        // 验证
        assertNotNull(result);
        verify(orderLifecycleService, times(1)).updateOrderStatus(1L, OrderStatus.CONFIRMED.name());
    }

    @Test
    void cancelOrder_Success() {
        // 准备
        mockSecurityContext();
        when(orderLifecycleService.cancelOrderWithReason(1L, "CANCELLED_BY_USER", "用户取消订单")).thenReturn(OrderDTO.builder().id(1L).status(OrderStatus.CANCELLED_BY_USER.name()).build());

        // 使用 CANCELLED_BY_USER 类型，因为当前用户是 ROLE_USER，
        // 而 CANCELLED 类型需要管理员权限
        OrderDTO result = orderService.cancelOrderWithReason(1L, "CANCELLED_BY_USER", "用户取消订单");

        // 验证
        assertNotNull(result);
        verify(orderLifecycleService, times(1)).cancelOrderWithReason(1L, "CANCELLED_BY_USER", "用户取消订单");
    }

    @Test
    void payOrder_Success() {
        // 准备
        mockSecurityContext();
        when(paymentProcessingService.processPayment(1L)).thenReturn(OrderDTO.builder().id(1L).status(OrderStatus.PAID.name()).paymentStatus(PaymentStatus.PAID.name()).build());

        // 执行
        OrderDTO result = orderService.payOrder(1L);

        // 验证
        assertNotNull(result);
        verify(paymentProcessingService, times(1)).processPayment(1L);
    }

    @Test
    void createOrderPreview_Success() {
        // 准备
        mockSecurityContext();
        when(homestayRepository.findById(1L)).thenReturn(Optional.of(homestay));
        when(orderRepository.existsOverlappingBooking(any(), any(), any())).thenReturn(false);

        // 执行
        OrderDTO result = orderService.createOrderPreview(orderDTO);

        // 验证
        assertNotNull(result);
        // baseAmount=200*2=400, cleaningFee=200*0.1=20, serviceFee=400*0.15=60,
        // total=480
        assertEquals(0, new BigDecimal("480").compareTo(result.getTotalAmount()));
        assertEquals(2, result.getNights());
    }

    @Test
    void createOrderPreview_InvalidCheckInDate() {
        // 准备
        mockSecurityContext();
        when(homestayRepository.findById(1L)).thenReturn(Optional.of(homestay));
        orderDTO.setCheckInDate(LocalDate.now().minusDays(1)); // 过去的日期

        // 执行和验证
        assertThrows(IllegalArgumentException.class, () -> {
            orderService.createOrderPreview(orderDTO);
        });
    }

    @Test
    void createOrderPreview_InvalidDateRange() {
        // 准备
        mockSecurityContext();
        when(homestayRepository.findById(1L)).thenReturn(Optional.of(homestay));
        orderDTO.setCheckInDate(LocalDate.now().plusDays(3));
        orderDTO.setCheckOutDate(LocalDate.now().plusDays(1)); // 退房早于入住

        // 执行和验证
        assertThrows(IllegalArgumentException.class, () -> {
            orderService.createOrderPreview(orderDTO);
        });
    }

    @Test
    void systemCancelOrder_Success() {
        // 准备
        when(orderLifecycleService.systemCancelOrder(1L, OrderStatus.CANCELLED_SYSTEM.name(), "系统超时自动取消")).thenReturn(OrderDTO.builder().id(1L).status(OrderStatus.CANCELLED_SYSTEM.name()).build());

        // 执行（系统取消不需要用户权限检查）
        OrderDTO result = orderService.systemCancelOrder(1L, OrderStatus.CANCELLED_SYSTEM.name(), "系统超时自动取消");

        // 验证
        assertNotNull(result);
        verify(orderLifecycleService, times(1)).systemCancelOrder(1L, OrderStatus.CANCELLED_SYSTEM.name(), "系统超时自动取消");
    }

    // ============================================================
    // 退款功能测试
    // ============================================================

    // --- 用户申请退款 requestUserRefund ---

    @Test
    void requestUserRefund_Success() {
        // 准备 - 已支付订单
        mockSecurityContext();
        when(paymentProcessingService.requestUserRefund(1L, "行程变更")).thenReturn(OrderDTO.builder()
                .id(1L)
                .status(OrderStatus.REFUND_PENDING.name())
                .paymentStatus(PaymentStatus.REFUND_PENDING.name())
                .refundType(RefundType.USER_REQUESTED.name())
                .refundReason("行程变更")
                .refundAmount(new BigDecimal("400"))
                .build());

        // 执行
        OrderDTO result = orderService.requestUserRefund(1L, "行程变更");

        // 验证
        assertNotNull(result);
        assertEquals(OrderStatus.REFUND_PENDING.name(), result.getStatus());
        assertEquals(PaymentStatus.REFUND_PENDING.name(), result.getPaymentStatus());
        assertEquals(RefundType.USER_REQUESTED.name(), result.getRefundType());
        assertEquals("行程变更", result.getRefundReason());
        assertNotNull(result.getRefundAmount());
        verify(paymentProcessingService, times(1)).requestUserRefund(1L, "行程变更");
    }

    @Test
    void requestUserRefund_WithReason() {
        // 准备
        mockSecurityContext();
        when(paymentProcessingService.requestUserRefund(1L, null)).thenReturn(OrderDTO.builder()
                .id(1L)
                .status(OrderStatus.REFUND_PENDING.name())
                .refundReason("用户申请退款")
                .build());

        // 执行 - 不提供原因
        OrderDTO result = orderService.requestUserRefund(1L, null);

        // 验证 - 应该使用默认原因
        assertNotNull(result);
        assertEquals("用户申请退款", result.getRefundReason());
        verify(paymentProcessingService, times(1)).requestUserRefund(1L, null);
    }

    @Test
    void requestUserRefund_NotPaid() {
        // 准备 - 未支付订单
        mockSecurityContext();
        when(paymentProcessingService.requestUserRefund(1L, "想退款")).thenThrow(new IllegalStateException("只有已支付的订单才能申请退款"));

        // 执行和验证 - 应抛出异常
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            orderService.requestUserRefund(1L, "想退款");
        });
        assertTrue(exception.getMessage().contains("已支付"));
    }

    @Test
    void requestUserRefund_AlreadyRefunding() {
        // 准备 - 订单已在退款流程中
        mockSecurityContext();
        when(paymentProcessingService.requestUserRefund(1L, "再次申请")).thenThrow(new IllegalStateException("订单已在退款流程中"));

        // 执行和验证 - 应抛出异常
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            orderService.requestUserRefund(1L, "再次申请");
        });
        assertTrue(exception.getMessage().contains("退款流程"));
    }

    @Test
    void requestUserRefund_OrderNotFound() {
        // 准备
        when(paymentProcessingService.requestUserRefund(999L, "退款")).thenThrow(new ResourceNotFoundException("订单不存在"));

        // 执行和验证
        assertThrows(ResourceNotFoundException.class, () -> {
            orderService.requestUserRefund(999L, "退款");
        });
    }

    // --- 管理员发起退款 initiateRefund ---
    // TODO: initiateRefund 方法不存在，跳过以下测试
    // @Test
    // void initiateRefund_Success() { ... }
    // @Test
    // void initiateRefund_NotPaid() { ... }

    // --- 管理员批准退款 approveRefund ---

    @Test
    void approveRefund_Success() {
        // 准备 - 退款中的订单
        mockSecurityContext();
        when(paymentProcessingService.approveRefund(1L, "同意退款")).thenReturn(OrderDTO.builder()
                .id(1L)
                .status(OrderStatus.REFUNDED.name())
                .paymentStatus(PaymentStatus.REFUNDED.name())
                .refundTransactionId("REFUND_TXN_001")
                .build());

        // 执行
        OrderDTO result = orderService.approveRefund(1L, "同意退款");

        // 验证
        assertNotNull(result);
        assertEquals(PaymentStatus.REFUNDED.name(), result.getPaymentStatus());
        assertEquals("REFUND_TXN_001", result.getRefundTransactionId());
        verify(paymentProcessingService, times(1)).approveRefund(1L, "同意退款");
    }

    @Test
    void approveRefund_GatewayFailed() {
        // 准备 - 退款中的订单
        when(paymentProcessingService.approveRefund(1L, "同意退款")).thenThrow(new RuntimeException("退款失败：余额不足"));

        // 执行和验证 - 应抛出异常
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            orderService.approveRefund(1L, "同意退款");
        });
        assertTrue(exception.getMessage().contains("退款失败") || exception.getMessage().contains("批准退款失败"));
    }

    @Test
    void approveRefund_NotRefundPending() {
        // 准备 - 非退款中状态
        when(paymentProcessingService.approveRefund(1L, "批准")).thenThrow(new IllegalStateException("订单不在退款中状态"));

        // 执行和验证
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            orderService.approveRefund(1L, "批准");
        });
        assertTrue(exception.getMessage().contains("退款中"));
    }

    // --- 管理员拒绝退款 rejectRefund ---

    @Test
    void rejectRefund_Success() {
        // 准备 - 退款中的订单
        mockSecurityContext();
        when(paymentProcessingService.rejectRefund(1L, "不符合退款条件")).thenReturn(OrderDTO.builder()
                .id(1L)
                .status(OrderStatus.PAID.name())
                .paymentStatus(PaymentStatus.PAID.name())
                .build());

        // 执行
        OrderDTO result = orderService.rejectRefund(1L, "不符合退款条件");

        // 验证 - 恢复已支付状态
        assertNotNull(result);
        assertEquals(PaymentStatus.PAID.name(), result.getPaymentStatus());
        assertEquals(OrderStatus.PAID.name(), result.getStatus());
        verify(paymentProcessingService, times(1)).rejectRefund(1L, "不符合退款条件");
    }

    @Test
    void rejectRefund_NotRefundPending() {
        // 准备 - 非退款中状态
        when(paymentProcessingService.rejectRefund(1L, "拒绝")).thenThrow(new IllegalStateException("订单不在退款中状态"));

        // 执行和验证
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            orderService.rejectRefund(1L, "拒绝");
        });
        assertTrue(exception.getMessage().contains("退款中"));
    }

    // --- 管理员完成退款 completeRefund ---
    // TODO: completeRefund 方法不存在，跳过以下测试
    // @Test
    // void completeRefund_Success() { ... }
    // @Test
    // void completeRefund_NotRefundPending() { ... }

    // --- 完整退款流程测试 ---

    @Test
    void refundFlow_UserRequest_AdminApprove() {
        // 准备
        mockSecurityContext();
        when(paymentProcessingService.requestUserRefund(1L, "行程有变")).thenReturn(OrderDTO.builder().status(OrderStatus.REFUND_PENDING.name()).paymentStatus(PaymentStatus.REFUND_PENDING.name()).build());
        when(paymentProcessingService.approveRefund(1L, "确认退款")).thenReturn(OrderDTO.builder().status(OrderStatus.REFUNDED.name()).paymentStatus(PaymentStatus.REFUNDED.name()).build());

        // 步骤1：用户申请退款
        OrderDTO step1Result = orderService.requestUserRefund(1L, "行程有变");
        assertEquals(OrderStatus.REFUND_PENDING.name(), step1Result.getStatus());
        assertEquals(PaymentStatus.REFUND_PENDING.name(), step1Result.getPaymentStatus());

        // 步骤2：管理员批准退款
        OrderDTO step2Result = orderService.approveRefund(1L, "确认退款");
        assertEquals(PaymentStatus.REFUNDED.name(), step2Result.getPaymentStatus());
        assertEquals(OrderStatus.REFUNDED.name(), step2Result.getStatus());
    }

    @Test
    void refundFlow_UserRequest_AdminReject() {
        // 准备
        mockSecurityContext();
        when(paymentProcessingService.requestUserRefund(1L, "不想住了")).thenReturn(OrderDTO.builder().status(OrderStatus.REFUND_PENDING.name()).build());
        when(paymentProcessingService.rejectRefund(1L, "超过退款期限")).thenReturn(OrderDTO.builder().status(OrderStatus.PAID.name()).paymentStatus(PaymentStatus.PAID.name()).build());

        // 步骤1：用户申请退款
        OrderDTO step1Result = orderService.requestUserRefund(1L, "不想住了");
        assertEquals(OrderStatus.REFUND_PENDING.name(), step1Result.getStatus());

        // 步骤2：管理员拒绝退款
        OrderDTO step2Result = orderService.rejectRefund(1L, "超过退款期限");
        assertEquals(PaymentStatus.PAID.name(), step2Result.getPaymentStatus());
        assertEquals(OrderStatus.PAID.name(), step2Result.getStatus());
    }
}
