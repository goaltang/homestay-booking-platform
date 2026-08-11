package com.homestay3.homestaybackend.service.impl;

import com.homestay3.homestaybackend.dto.OrderDTO;
import com.homestay3.homestaybackend.dto.payment.PaymentNotifyResult;
import com.homestay3.homestaybackend.dto.payment.PaymentResponse;
import com.homestay3.homestaybackend.dto.payment.PaymentStatusResponse;
import com.homestay3.homestaybackend.dto.refund.RefundRequest;
import com.homestay3.homestaybackend.dto.refund.RefundResponse;
import com.homestay3.homestaybackend.entity.RefundRecord;
import com.homestay3.homestaybackend.entity.User;
import com.homestay3.homestaybackend.entity.Homestay;
import com.homestay3.homestaybackend.entity.Order;
import com.homestay3.homestaybackend.entity.PaymentRecord;
import com.homestay3.homestaybackend.model.OrderStatus;
import com.homestay3.homestaybackend.model.PaymentStatus;
import com.homestay3.homestaybackend.model.RefundStatus;
import com.homestay3.homestaybackend.model.RefundType;
import com.homestay3.homestaybackend.repository.OrderRepository;
import com.homestay3.homestaybackend.repository.PaymentRecordRepository;
import com.homestay3.homestaybackend.repository.RefundRecordRepository;
import com.homestay3.homestaybackend.service.OrderService;
import com.homestay3.homestaybackend.service.PaymentProcessingService;
import com.homestay3.homestaybackend.service.gateway.AlipayGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * PaymentService 单元测试
 * 全面覆盖支付核心业务逻辑：支付创建、状态查询、模拟支付、回调处理、退款
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRecordRepository paymentRecordRepository;

    @Mock
    private RefundRecordRepository refundRecordRepository;

    @Mock
    private OrderService orderService;

    @Mock
    private PaymentProcessingService paymentProcessingService;

    @Mock
    private AlipayGateway alipayGateway;

    @Spy
    private OrderStatusUpdater orderStatusUpdater = new OrderStatusUpdater();

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private User currentUser;
    private User hostUser;
    private Order order;
    private Homestay homestay;
    private PaymentRecord paymentRecord;

    @BeforeEach
    void setUp() {
        // 设置当前用户（房客）
        currentUser = new User();
        currentUser.setId(1L);
        currentUser.setUsername("guestuser");
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
        homestay.setOwner(hostUser);

        // 设置订单
        order = new Order();
        order.setId(1L);
        order.setOrderNumber("ORD202401010001");
        order.setHomestay(homestay);
        order.setGuest(currentUser);
        order.setGuestPhone("13800138000");
        order.setTotalAmount(new BigDecimal("400"));
        order.setStatus(OrderStatus.CONFIRMED.name());
        order.setPaymentStatus(PaymentStatus.UNPAID);

        // 设置支付记录
        paymentRecord = new PaymentRecord();
        paymentRecord.setId(1L);
        paymentRecord.setOrderId(1L);
        paymentRecord.setOutTradeNo("HST202401011200001001");
        paymentRecord.setPaymentMethod("ALIPAY");
        paymentRecord.setAmount(new BigDecimal("400"));
        paymentRecord.setStatus("PENDING");

        // 手动注入 @Autowired @Lazy 的 orderService（Mockito @InjectMocks 不处理 field
        // injection）
        ReflectionTestUtils.setField(paymentService, "orderService", orderService);
        ReflectionTestUtils.setField(paymentService, "paymentProcessingService", paymentProcessingService);
    }

    // ============================================================
    // checkPaymentStatus 支付状态查询测试
    // ============================================================

    @Nested
    @DisplayName("checkPaymentStatus - 支付状态查询")
    class CheckPaymentStatusTests {

        @Test
        @DisplayName("支付记录已成功 - 直接返回true")
        void checkPaymentStatus_PaidViaRecord() {
            paymentRecord.setStatus("SUCCESS");
            when(paymentRecordRepository.findTopByOrderIdOrderByCreatedAtDesc(1L))
                    .thenReturn(Optional.of(paymentRecord));

            boolean isPaid = paymentService.checkPaymentStatus(1L);

            assertTrue(isPaid);
            // 不应该调用网关查询（因为本地已成功）
            verify(alipayGateway, never()).queryPayment(anyString());
        }

        @Test
        @DisplayName("支付记录存在但未支付 - 调用网关查询并返回false")
        void checkPaymentStatus_Unpaid() {
            when(paymentRecordRepository.findTopByOrderIdOrderByCreatedAtDesc(1L))
                    .thenReturn(Optional.of(paymentRecord));
            // 网关查询返回未支付
            PaymentStatusResponse gatewayResponse = PaymentStatusResponse.builder()
                    .success(true)
                    .status(PaymentStatus.UNPAID)
                    .build();
            when(alipayGateway.queryPayment(paymentRecord.getOutTradeNo()))
                    .thenReturn(gatewayResponse);

            boolean isPaid = paymentService.checkPaymentStatus(1L);

            assertFalse(isPaid);
            verify(alipayGateway).queryPayment(paymentRecord.getOutTradeNo());
        }

        @Test
        @DisplayName("无支付记录 - 返回false")
        void checkPaymentStatus_NoRecord() {
            when(paymentRecordRepository.findTopByOrderIdOrderByCreatedAtDesc(999L))
                    .thenReturn(Optional.empty());

            boolean isPaid = paymentService.checkPaymentStatus(999L);

            assertFalse(isPaid);
        }

        @Test
        @DisplayName("网关查询确认已支付 - 更新记录并返回true")
        void checkPaymentStatus_PaidViaGateway() {
            when(paymentRecordRepository.findTopByOrderIdOrderByCreatedAtDesc(1L))
                    .thenReturn(Optional.of(paymentRecord));

            // 网关返回已支付
            PaymentStatusResponse gatewayResponse = PaymentStatusResponse.builder()
                    .success(true)
                    .status(PaymentStatus.PAID)
                    .transactionId("ALIPAY_TXN_123")
                    .build();
            when(alipayGateway.queryPayment(paymentRecord.getOutTradeNo()))
                    .thenReturn(gatewayResponse);

            // mock 订单查询（updateOrderPaymentStatus 内部用到）
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenReturn(order);
            doNothing().when(paymentProcessingService).handleOrderPaidSuccess(anyLong());

            boolean isPaid = paymentService.checkPaymentStatus(1L);

            assertTrue(isPaid);
            // 确认更新了支付记录
            assertEquals("SUCCESS", paymentRecord.getStatus());
            assertEquals("ALIPAY_TXN_123", paymentRecord.getTransactionId());
            verify(paymentRecordRepository).save(paymentRecord);
            // 确认更新了订单状态
            verify(orderRepository).save(any(Order.class));
            // 确认触发统一支付成功后置处理
            verify(paymentProcessingService).handleOrderPaidSuccess(1L);
        }

        @Test
        @DisplayName("网关查询失败 - 返回false")
        void checkPaymentStatus_GatewayQueryFailed() {
            when(paymentRecordRepository.findTopByOrderIdOrderByCreatedAtDesc(1L))
                    .thenReturn(Optional.of(paymentRecord));

            PaymentStatusResponse gatewayResponse = PaymentStatusResponse.builder()
                    .success(false)
                    .message("查询失败")
                    .build();
            when(alipayGateway.queryPayment(paymentRecord.getOutTradeNo()))
                    .thenReturn(gatewayResponse);

            boolean isPaid = paymentService.checkPaymentStatus(1L);

            assertFalse(isPaid);
        }

        @Test
        @DisplayName("网关查询抛出异常 - 安全返回false")
        void checkPaymentStatus_GatewayThrowsException() {
            when(paymentRecordRepository.findTopByOrderIdOrderByCreatedAtDesc(1L))
                    .thenReturn(Optional.of(paymentRecord));
            when(alipayGateway.queryPayment(anyString()))
                    .thenThrow(new RuntimeException("网络异常"));

            boolean isPaid = paymentService.checkPaymentStatus(1L);

            assertFalse(isPaid);
        }
    }

    // ============================================================
    // mockSuccessPayment 模拟支付测试
    // ============================================================

    @Nested
    @DisplayName("mockSuccessPayment - 模拟支付成功")
    class MockSuccessPaymentTests {

        @Test
        @DisplayName("正常模拟支付成功")
        void mockSuccessPayment_Success() {
            order.setGuest(currentUser);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(orderService.updateOrderStatus(anyLong(), anyString())).thenReturn(new OrderDTO());

            OrderDTO result = paymentService.mockSuccessPayment(1L);

            assertNotNull(result);
            verify(orderService, times(1)).updateOrderStatus(1L, OrderStatus.PAID.name());
        }

        @Test
        @DisplayName("已支付订单 - 抛出异常")
        void mockSuccessPayment_AlreadyPaid() {
            order.setPaymentStatus(PaymentStatus.PAID);
            order.setStatus(OrderStatus.PAID.name());
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                paymentService.mockSuccessPayment(1L);
            });

            assertTrue(exception.getMessage().contains("已支付"));
        }

        @Test
        @DisplayName("订单不存在 - 抛出异常")
        void mockSuccessPayment_OrderNotFound() {
            when(orderRepository.findById(999L)).thenReturn(Optional.empty());

            RuntimeException exception = assertThrows(RuntimeException.class, () -> {
                paymentService.mockSuccessPayment(999L);
            });

            assertTrue(exception.getMessage().contains("不存在"));
        }

        @Test
        @DisplayName("订单已完成 - 不能支付")
        void mockSuccessPayment_CompletedOrder() {
            order.setStatus(OrderStatus.COMPLETED.name());
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                paymentService.mockSuccessPayment(1L);
            });

            assertTrue(exception.getMessage().contains("状态不正确"));
        }

        @Test
        @DisplayName("订单已取消 - 不能支付")
        void mockSuccessPayment_CancelledOrder() {
            order.setStatus(OrderStatus.CANCELLED.name());
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                paymentService.mockSuccessPayment(1L);
            });

            assertTrue(exception.getMessage().contains("状态不正确"));
        }
    }

    // ============================================================
    // generatePaymentQRCode 支付创建测试
    // ============================================================

    @Nested
    @DisplayName("generatePaymentQRCode - 生成支付")
    class GeneratePaymentQRCodeTests {

        @Test
        @DisplayName("二维码支付生成成功")
        void generatePaymentQRCode_QRCodeSuccess() {
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenReturn(order);

            PaymentResponse qrResponse = PaymentResponse.builder()
                    .success(true)
                    .qrCode("https://qr.alipay.com/test_qr_code")
                    .outTradeNo("HST_TEST_001")
                    .message("二维码生成成功")
                    .build();
            when(alipayGateway.createQRCodePayment(any())).thenReturn(qrResponse);

            String result = paymentService.generatePaymentQRCode(1L, "alipay");

            assertNotNull(result);
            assertEquals("https://qr.alipay.com/test_qr_code", result);
            verify(paymentRecordRepository).save(any(PaymentRecord.class));
            verify(orderRepository).save(any(Order.class));
        }

        @Test
        @DisplayName("二维码生成失败 - 抛出异常")
        void generatePaymentQRCode_QRCodeFailed() {
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            // 二维码支付失败
            PaymentResponse qrFailed = PaymentResponse.builder()
                    .success(false)
                    .message("当面付权限不足")
                    .build();
            when(alipayGateway.createQRCodePayment(any())).thenReturn(qrFailed);

            RuntimeException exception = assertThrows(RuntimeException.class, () -> {
                paymentService.generatePaymentQRCode(1L, "alipay");
            });

            assertTrue(exception.getMessage().contains("生成支付失败"));
            verify(alipayGateway).createQRCodePayment(any());
        }

        @Test
        @DisplayName("订单不存在 - 抛出异常")
        void generatePaymentQRCode_OrderNotFound() {
            when(orderRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(RuntimeException.class, () -> {
                paymentService.generatePaymentQRCode(999L, "alipay");
            });
        }

        @Test
        @DisplayName("订单已完成 - 不能支付")
        void generatePaymentQRCode_CompletedOrder() {
            order.setStatus(OrderStatus.COMPLETED.name());
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            assertThrows(RuntimeException.class, () -> {
                paymentService.generatePaymentQRCode(1L, "alipay");
            });
        }

        @Test
        @DisplayName("订单已取消 - 不能支付")
        void generatePaymentQRCode_CancelledOrder() {
            order.setStatus(OrderStatus.CANCELLED.name());
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            assertThrows(RuntimeException.class, () -> {
                paymentService.generatePaymentQRCode(1L, "alipay");
            });
        }

        @Test
        @DisplayName("支付网关返回错误信息 - 抛出异常包含错误详情")
        void generatePaymentQRCode_GatewayError() {
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            PaymentResponse qrFailed = PaymentResponse.builder()
                    .success(false)
                    .message("ISV权限不足")
                    .errorCode("isv.insufficient-isv-permissions")
                    .build();
            when(alipayGateway.createQRCodePayment(any())).thenReturn(qrFailed);

            RuntimeException exception = assertThrows(RuntimeException.class, () -> {
                paymentService.generatePaymentQRCode(1L, "alipay");
            });

            assertTrue(exception.getMessage().contains("生成支付失败"));
            assertTrue(exception.getMessage().contains("ISV权限不足"));
        }

        @Test
        @DisplayName("支付成功后订单状态更新为 PAYMENT_PENDING")
        void generatePaymentQRCode_UpdatesOrderStatus() {
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            PaymentResponse qrResponse = PaymentResponse.builder()
                    .success(true)
                    .qrCode("https://qr.alipay.com/test")
                    .outTradeNo("HST_TEST")
                    .build();
            when(alipayGateway.createQRCodePayment(any())).thenReturn(qrResponse);

            paymentService.generatePaymentQRCode(1L, "alipay");

            assertEquals(OrderStatus.PAYMENT_PENDING.name(), order.getStatus());
            verify(orderRepository).save(order);
        }
    }

    // ============================================================
    // handlePaymentNotify 支付回调处理测试
    // ============================================================

    @Nested
    @DisplayName("handlePaymentNotify - 支付回调处理")
    class HandlePaymentNotifyTests {

        private PaymentNotifyResult createNotifyResult() {
            Map<String, String> params = new HashMap<>();
            params.put("out_trade_no", "HST202401011200001001");
            params.put("trade_no", "ALIPAY_TXN_456");
            params.put("trade_status", "TRADE_SUCCESS");

            return PaymentNotifyResult.builder()
                    .success(true)
                    .outTradeNo("HST202401011200001001")
                    .transactionId("ALIPAY_TXN_456")
                    .amount(new BigDecimal("400"))
                    .params(params)
                    .message("支付成功")
                    .build();
        }


        @Test
        @DisplayName("正常回调处理成功")
        void handlePaymentNotify_Success() {
            PaymentNotifyResult result = createNotifyResult();
            when(paymentRecordRepository.findByOutTradeNo("HST202401011200001001"))
                    .thenReturn(Optional.of(paymentRecord));
            when(paymentRecordRepository.save(any(PaymentRecord.class))).thenReturn(paymentRecord);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenReturn(order);
            doNothing().when(paymentProcessingService).handleOrderPaidSuccess(anyLong());

            assertDoesNotThrow(() -> paymentService.handlePaymentNotify(result));

            assertEquals("SUCCESS", paymentRecord.getStatus());
            assertEquals("ALIPAY_TXN_456", paymentRecord.getTransactionId());
            verify(paymentRecordRepository).save(paymentRecord);
            verify(orderRepository).save(any(Order.class));
            verify(paymentProcessingService).handleOrderPaidSuccess(1L);
        }

        @Test
        @DisplayName("重复回调 - 幂等跳过（已处理）")
        void handlePaymentNotify_DuplicateSkipped() {
            PaymentNotifyResult result = createNotifyResult();
            paymentRecord.setStatus("SUCCESS"); // 已经处理过
            when(paymentRecordRepository.findByOutTradeNo("HST202401011200001001"))
                    .thenReturn(Optional.of(paymentRecord));

            assertDoesNotThrow(() -> paymentService.handlePaymentNotify(result));

            // 不应该再次保存或更新订单
            verify(paymentRecordRepository, never()).save(any(PaymentRecord.class));
            verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        @DisplayName("乐观锁冲突 - 安全跳过")
        void handlePaymentNotify_OptimisticLockConflict() {
            PaymentNotifyResult result = createNotifyResult();
            when(paymentRecordRepository.findByOutTradeNo("HST202401011200001001"))
                    .thenReturn(Optional.of(paymentRecord));
            when(paymentRecordRepository.save(any(PaymentRecord.class)))
                    .thenThrow(new ObjectOptimisticLockingFailureException(PaymentRecord.class, 1L));

            // 乐观锁冲突不应该抛出异常到调用者
            assertDoesNotThrow(() -> paymentService.handlePaymentNotify(result));
        }

        @Test
        @DisplayName("支付记录不存在 - 抛出异常")
        void handlePaymentNotify_RecordNotFound() {
            PaymentNotifyResult result = createNotifyResult();
            when(paymentRecordRepository.findByOutTradeNo("HST202401011200001001"))
                    .thenReturn(Optional.empty());

            assertThrows(RuntimeException.class, () -> {
                paymentService.handlePaymentNotify(result);
            });
        }
    }

    // ============================================================
    // processRefund 退款处理测试
    // ============================================================

    @Nested
    @DisplayName("processRefund - 退款处理")
    class ProcessRefundTests {

        @Test
        @DisplayName("退款成功")
        void processRefund_Success() {
            order.setPaymentStatus(PaymentStatus.PAID);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(paymentRecordRepository.findTopByOrderIdOrderByCreatedAtDesc(1L))
                    .thenReturn(Optional.of(paymentRecord));

            RefundResponse gatewayResponse = RefundResponse.builder()
                    .success(true)
                    .outTradeNo("HST202401011200001001")
                    .refundTradeNo("REFUND_TXN_789")
                    .refundAmount(new BigDecimal("400"))
                    .message("退款成功")
                    .build();
            when(alipayGateway.processRefund(any(RefundRequest.class))).thenReturn(gatewayResponse);
            when(refundRecordRepository.save(any(RefundRecord.class))).thenAnswer(inv -> inv.getArgument(0));

            RefundRequest request = RefundRequest.builder()
                    .orderId(1L)
                    .refundAmount(new BigDecimal("400"))
                    .refundReason("用户申请退款")
                    .refundType(RefundType.USER_REQUESTED)
                    .paymentMethod("alipay")
                    .build();

            RefundResponse result = paymentService.processRefund(request);

            assertTrue(result.isSuccess());
            assertEquals("REFUND_TXN_789", result.getRefundTradeNo());
            verify(refundRecordRepository).save(any(RefundRecord.class));
        }

        @Test
        @DisplayName("退款失败 - 网关返回失败")
        void processRefund_GatewayFailed() {
            order.setPaymentStatus(PaymentStatus.PAID);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(paymentRecordRepository.findTopByOrderIdOrderByCreatedAtDesc(1L))
                    .thenReturn(Optional.of(paymentRecord));

            RefundResponse gatewayResponse = RefundResponse.builder()
                    .success(false)
                    .message("余额不足")
                    .errorCode("INSUFFICIENT_BALANCE")
                    .build();
            when(alipayGateway.processRefund(any(RefundRequest.class))).thenReturn(gatewayResponse);
            when(refundRecordRepository.save(any(RefundRecord.class))).thenAnswer(inv -> inv.getArgument(0));

            RefundRequest request = RefundRequest.builder()
                    .orderId(1L)
                    .refundAmount(new BigDecimal("400"))
                    .refundReason("退款")
                    .paymentMethod("alipay")
                    .build();

            RefundResponse result = paymentService.processRefund(request);

            assertFalse(result.isSuccess());
            // 失败也应保存退款记录
            verify(refundRecordRepository).save(any(RefundRecord.class));
        }

        @Test
        @DisplayName("订单不存在 - 返回错误响应")
        void processRefund_OrderNotFound() {
            when(orderRepository.findById(999L)).thenReturn(Optional.empty());

            RefundRequest request = RefundRequest.builder()
                    .orderId(999L)
                    .refundAmount(new BigDecimal("400"))
                    .paymentMethod("alipay")
                    .build();

            RefundResponse result = paymentService.processRefund(request);

            assertFalse(result.isSuccess());
            assertTrue(result.getMessage().contains("失败"));
        }

        @Test
        @DisplayName("订单未支付 - 拒绝退款")
        void processRefund_NotPaid() {
            order.setPaymentStatus(PaymentStatus.UNPAID);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            RefundRequest request = RefundRequest.builder()
                    .orderId(1L)
                    .refundAmount(new BigDecimal("400"))
                    .paymentMethod("alipay")
                    .build();

            RefundResponse result = paymentService.processRefund(request);

            assertFalse(result.isSuccess());
            assertTrue(result.getMessage().contains("失败") || result.getMessage().contains("不支持"));
        }

        @Test
        @DisplayName("退款中状态 - 允许退款")
        void processRefund_RefundPendingStatus() {
            order.setPaymentStatus(PaymentStatus.REFUND_PENDING);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(paymentRecordRepository.findTopByOrderIdOrderByCreatedAtDesc(1L))
                    .thenReturn(Optional.of(paymentRecord));

            RefundResponse gatewayResponse = RefundResponse.builder()
                    .success(true)
                    .refundTradeNo("REFUND_OK")
                    .refundAmount(new BigDecimal("400"))
                    .message("退款成功")
                    .build();
            when(alipayGateway.processRefund(any(RefundRequest.class))).thenReturn(gatewayResponse);
            when(refundRecordRepository.save(any(RefundRecord.class))).thenAnswer(inv -> inv.getArgument(0));

            RefundRequest request = RefundRequest.builder()
                    .orderId(1L)
                    .refundAmount(new BigDecimal("400"))
                    .paymentMethod("alipay")
                    .build();

            RefundResponse result = paymentService.processRefund(request);

            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("支付记录不存在 - 返回错误")
        void processRefund_NoPaymentRecord() {
            order.setPaymentStatus(PaymentStatus.PAID);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(paymentRecordRepository.findTopByOrderIdOrderByCreatedAtDesc(1L))
                    .thenReturn(Optional.empty());

            RefundRequest request = RefundRequest.builder()
                    .orderId(1L)
                    .refundAmount(new BigDecimal("400"))
                    .paymentMethod("alipay")
                    .build();

            RefundResponse result = paymentService.processRefund(request);

            assertFalse(result.isSuccess());
            assertTrue(result.getMessage().contains("失败"));
        }
    }
}
