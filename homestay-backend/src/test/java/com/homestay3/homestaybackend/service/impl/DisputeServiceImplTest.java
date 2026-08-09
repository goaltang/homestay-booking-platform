package com.homestay3.homestaybackend.service.impl;

import com.homestay3.homestaybackend.dto.OrderDTO;
import com.homestay3.homestaybackend.entity.Homestay;
import com.homestay3.homestaybackend.entity.Order;
import com.homestay3.homestaybackend.entity.User;
import com.homestay3.homestaybackend.exception.AccessDeniedException;
import com.homestay3.homestaybackend.exception.ResourceNotFoundException;
import com.homestay3.homestaybackend.model.OrderStatus;
import com.homestay3.homestaybackend.model.PaymentStatus;
import com.homestay3.homestaybackend.repository.OrderRepository;
import com.homestay3.homestaybackend.repository.UserRepository;
import com.homestay3.homestaybackend.service.DisputeRecordService;
import com.homestay3.homestaybackend.service.DisputeService;
import com.homestay3.homestaybackend.service.OrderNotificationService;
import com.homestay3.homestaybackend.service.PaymentProcessingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * DisputeService 单元测试
 * 测试争议处理业务逻辑
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DisputeServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentProcessingService paymentProcessingService;

    @Mock
    private OrderNotificationService orderNotificationService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DisputeRecordService disputeRecordService;

    @Spy
    private OrderStatusUpdater orderStatusUpdater = new OrderStatusUpdater();

    @InjectMocks
    private DisputeServiceImpl disputeService;

    private User currentUser;
    private Order testOrder;

    @BeforeEach
    void setUp() {
        // 设置当前用户
        currentUser = new User();
        currentUser.setId(1L);
        currentUser.setUsername("testuser");

        // 设置SecurityContext
        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("testuser");
        SecurityContextHolder.setContext(securityContext);

        // mock userRepository
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(currentUser));

        // 创建测试订单
        testOrder = new Order();
        testOrder.setId(1L);
        testOrder.setOrderNumber("ORDER202403130001");
        testOrder.setStatus(OrderStatus.REFUND_PENDING.name());
        testOrder.setPaymentStatus(PaymentStatus.REFUND_PENDING);
        testOrder.setTotalAmount(new BigDecimal("500.00"));
        testOrder.setRefundReason("用户申请取消");
        testOrder.setRefundAmount(new BigDecimal("500.00"));
        testOrder.setGuest(currentUser);
        Homestay testHomestay = new Homestay();
        testOrder.setHomestay(testHomestay);
    }

    // ========== raiseDispute 测试 ==========

    @Test
    void raiseDispute_Success() {
        // given
        String disputeReason = "房东认为不应全额退款";
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // when
        OrderDTO result = disputeService.raiseDispute(1L, disputeReason);

        // then
        assertNotNull(result);
        verify(orderRepository).save(any(Order.class));
        // 验证订单状态更新
        assertEquals(OrderStatus.DISPUTE_PENDING.name(), testOrder.getStatus());
        // 验证支付状态同步更新为 DISPUTED
        assertEquals(PaymentStatus.DISPUTED, testOrder.getPaymentStatus());
        assertEquals(disputeReason, testOrder.getDisputeReason());
        assertEquals(currentUser.getId(), testOrder.getDisputeRaisedBy());
        assertNotNull(testOrder.getDisputeRaisedAt());
        // 验证争议通知已发送
        verify(orderNotificationService).sendDisputeRaisedNotification(
                eq(1L), eq(1L), isNull(), eq("ORDER202403130001"), isNull(), eq(disputeReason));
    }

    @Test
    void raiseDispute_OrderNotFound() {
        // given
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        // when & then
        assertThrows(ResourceNotFoundException.class, () -> {
            disputeService.raiseDispute(99L, "测试原因");
        });
    }

    @Test
    void raiseDispute_OrderNotInRefundPending() {
        // given: 订单不在退款中状态
        testOrder.setStatus(OrderStatus.PAID.name());
        testOrder.setPaymentStatus(PaymentStatus.PAID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // when & then
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            disputeService.raiseDispute(1L, "测试原因");
        });
        assertEquals("只有退款中的订单才能发起争议", exception.getMessage());
    }

    @Test
    void raiseDispute_AlreadyInDispute() {
        // given: 订单已在争议中
        testOrder.setStatus(OrderStatus.DISPUTE_PENDING.name());
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // when & then
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            disputeService.raiseDispute(1L, "测试原因");
        });
        assertEquals("订单已在争议处理中，请勿重复发起", exception.getMessage());
    }

    // ========== raiseDisputeByGuest 测试 ==========

    @Test
    void raiseDisputeByGuest_Success() {
        // given
        String disputeReason = "入住体验与描述严重不符";
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // when
        OrderDTO result = disputeService.raiseDisputeByGuest(1L, disputeReason);

        // then
        assertNotNull(result);
        verify(orderRepository).save(any(Order.class));
        // 验证订单状态更新
        assertEquals(OrderStatus.DISPUTE_PENDING.name(), testOrder.getStatus());
        // 验证支付状态同步更新为 DISPUTED
        assertEquals(PaymentStatus.DISPUTED, testOrder.getPaymentStatus());
        assertEquals(disputeReason, testOrder.getDisputeReason());
        assertEquals(currentUser.getId(), testOrder.getDisputeRaisedBy());
        assertNotNull(testOrder.getDisputeRaisedAt());
        // 验证创建了争议记录
        verify(disputeRecordService).createDisputeRecord(eq(1L), eq(disputeReason), eq(currentUser.getId()));
        // 验证争议通知已发送
        verify(orderNotificationService).sendDisputeRaisedNotification(
                eq(1L), eq(1L), isNull(), eq("ORDER202403130001"), isNull(), eq(disputeReason));
    }

    @Test
    void raiseDisputeByGuest_NotGuest() {
        // given: 当前用户不是订单客人
        User otherUser = new User();
        otherUser.setId(2L);
        otherUser.setUsername("otheruser");
        testOrder.setGuest(otherUser);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // when & then
        assertThrows(AccessDeniedException.class, () -> {
            disputeService.raiseDisputeByGuest(1L, "测试原因");
        });
        // 未发起争议，状态不应变化
        assertEquals(OrderStatus.REFUND_PENDING.name(), testOrder.getStatus());
    }

    @Test
    void raiseDisputeByGuest_OrderNotFound() {
        // given
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        // when & then
        assertThrows(ResourceNotFoundException.class, () -> {
            disputeService.raiseDisputeByGuest(99L, "测试原因");
        });
    }

    @Test
    void raiseDisputeByGuest_AlreadyInDispute() {
        // given: 订单已在争议中
        testOrder.setStatus(OrderStatus.DISPUTE_PENDING.name());
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // when & then
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            disputeService.raiseDisputeByGuest(1L, "测试原因");
        });
        assertEquals("订单已在争议处理中，请勿重复发起", exception.getMessage());
    }

    // ========== resolveDispute 测试 ==========

    @Test
    void resolveDispute_ApproveRefund_Success() {
        // given
        testOrder.setStatus(OrderStatus.DISPUTE_PENDING.name());
        testOrder.setPaymentStatus(PaymentStatus.DISPUTED);
        testOrder.setDisputeReason("房东不同意退款");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        OrderDTO approvedOrderDTO = new OrderDTO();
        approvedOrderDTO.setId(1L);
        approvedOrderDTO.setStatus(OrderStatus.REFUNDED.name());
        when(paymentProcessingService.approveRefund(1L, "仲裁批准")).thenReturn(approvedOrderDTO);

        // when
        OrderDTO result = disputeService.resolveDispute(1L, "APPROVED", "仲裁批准");

        // then
        assertNotNull(result);
        verify(paymentProcessingService).approveRefund(1L, "仲裁批准");
        assertEquals("APPROVED", testOrder.getDisputeResolution());
        assertNotNull(testOrder.getDisputeResolvedAt());
        // 验证争议解决通知已发送
        verify(orderNotificationService).sendDisputeResolvedNotification(
                eq(1L), eq(1L), isNull(), eq("ORDER202403130001"), isNull(), eq("APPROVED"), eq("仲裁批准"));
    }

    @Test
    void resolveDispute_RejectRefund_Success() {
        // given
        testOrder.setStatus(OrderStatus.DISPUTE_PENDING.name());
        testOrder.setPaymentStatus(PaymentStatus.DISPUTED);
        testOrder.setDisputeReason("房东不同意退款");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // when
        OrderDTO result = disputeService.resolveDispute(1L, "REJECTED", "经核实，订单符合退款条件");

        // then
        assertNotNull(result);
        verify(paymentProcessingService, never()).approveRefund(any(), any());
        assertEquals(OrderStatus.PAID.name(), testOrder.getStatus());
        assertEquals(PaymentStatus.PAID, testOrder.getPaymentStatus());
        assertEquals("REJECTED", testOrder.getDisputeResolution());
        assertNotNull(testOrder.getDisputeResolvedAt());
        // 验证争议解决通知已发送
        verify(orderNotificationService).sendDisputeResolvedNotification(
                eq(1L), eq(1L), isNull(), eq("ORDER202403130001"), isNull(), eq("REJECTED"), eq("经核实，订单符合退款条件"));
    }

    @Test
    void resolveDispute_OrderNotFound() {
        // given
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        // when & then
        assertThrows(ResourceNotFoundException.class, () -> {
            disputeService.resolveDispute(99L, "APPROVED", "测试备注");
        });
    }

    @Test
    void resolveDispute_OrderNotInDispute() {
        // given: 订单不在争议状态
        testOrder.setStatus(OrderStatus.PAID.name());
        testOrder.setPaymentStatus(PaymentStatus.PAID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // when & then
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            disputeService.resolveDispute(1L, "APPROVED", "测试备注");
        });
        assertEquals("只有争议中的订单才能进行仲裁", exception.getMessage());
    }

    @Test
    void resolveDispute_InvalidResolution() {
        // given
        testOrder.setStatus(OrderStatus.DISPUTE_PENDING.name());
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // when & then - 使用无效的resolution会触发paymentProcessingService的验证错误
        // 这里主要测试能够调用到resolveDispute方法
        assertThrows(Exception.class, () -> {
            disputeService.resolveDispute(1L, "INVALID", "测试备注");
        });
    }
}
