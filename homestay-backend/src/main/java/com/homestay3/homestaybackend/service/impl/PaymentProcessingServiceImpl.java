package com.homestay3.homestaybackend.service.impl;

import com.homestay3.homestaybackend.dto.EarningDTO;
import com.homestay3.homestaybackend.dto.OrderDTO;
import com.homestay3.homestaybackend.dto.refund.RefundRequest;
import com.homestay3.homestaybackend.dto.refund.RefundResponse;
import com.homestay3.homestaybackend.entity.Order;
import com.homestay3.homestaybackend.entity.PaymentRecord;
import com.homestay3.homestaybackend.model.OrderStatus;
import com.homestay3.homestaybackend.model.PaymentStatus;
import com.homestay3.homestaybackend.model.RefundType;
import com.homestay3.homestaybackend.entity.User;
import com.homestay3.homestaybackend.exception.ResourceNotFoundException;
import com.homestay3.homestaybackend.repository.OrderRepository;
import com.homestay3.homestaybackend.repository.PaymentRecordRepository;
import com.homestay3.homestaybackend.repository.PromotionUsageRepository;
import com.homestay3.homestaybackend.repository.UserRepository;
import com.homestay3.homestaybackend.service.CouponService;
import com.homestay3.homestaybackend.service.EarningService;
import com.homestay3.homestaybackend.service.OrderNotificationService;
import com.homestay3.homestaybackend.service.PaymentProcessingService;
import com.homestay3.homestaybackend.service.PaymentService;
import com.homestay3.homestaybackend.service.RefundPolicyCalculator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付处理服务实现
 * 封装所有与订单支付相关的业务逻辑
 */
@Service
@RequiredArgsConstructor
public class PaymentProcessingServiceImpl implements PaymentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessingServiceImpl.class);
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final EarningService earningService;
    private final OrderNotificationService orderNotificationService;
    private final PaymentService paymentService;
    private final RefundPolicyCalculator refundPolicyCalculator;
    private final CouponService couponService;
    private final PromotionUsageRepository promotionUsageRepository;
    private final com.homestay3.homestaybackend.repository.UserCouponRepository userCouponRepository;
    private final com.homestay3.homestaybackend.service.CouponAnalyticsService couponAnalyticsService;
    private final OrderDtoAssembler orderDtoAssembler;
    private final OrderStatusUpdater orderStatusUpdater;

    @Override
    @Transactional
    public OrderDTO processPayment(Long orderId) {
        // 使用默认支付方式
        return processPayment(orderId, "default");
    }

    @Override
    @Transactional
    public OrderDTO processPayment(Long orderId, String paymentMethod) {
        log.info("开始处理订单 ID: {} 的支付请求，支付方式: {}", orderId, paymentMethod);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("订单不存在"));
        log.debug("找到订单: {}, 当前状态: {}", order.getOrderNumber(), order.getStatus());

        // 检查当前用户是否有权限
        User currentUser = getCurrentUser();
        log.debug("执行支付操作的用户: {}", currentUser.getUsername());
        if (!isOrderGuest(order, currentUser)) {
            log.warn("用户 {} 尝试支付不属于自己的订单 {}，操作被拒绝。", currentUser.getUsername(), order.getOrderNumber());
            throw new IllegalArgumentException("只有订单的客户可以支付订单");
        }

        // 检查订单状态
        OrderStatus currentStatus = OrderStatus.valueOf(order.getStatus());
        log.debug("检查订单 {} 的状态是否允许支付 (当前: {})", order.getOrderNumber(), currentStatus);
        if (currentStatus != OrderStatus.CONFIRMED && currentStatus != OrderStatus.PAYMENT_PENDING) {
            log.warn("订单 {} 状态为 {}，不允许支付。", order.getOrderNumber(), currentStatus);
            throw new IllegalArgumentException("只有已确认或正在支付中的订单可以支付");
        }

        // 模拟支付处理
        log.debug("开始模拟订单 {} 的支付处理...", order.getOrderNumber());
        try {
            // 模拟支付处理延迟
            Thread.sleep(500);
            log.debug("模拟支付处理完成，准备更新订单 {} 状态为 PAID。", order.getOrderNumber());

            // 支付成功，统一更新订单状态
            orderStatusUpdater.markPaid(order);

            // 添加支付备注
            String remark = (order.getRemark() != null && !order.getRemark().isEmpty()) ? order.getRemark() + "\n" : "";
            remark += "支付方式: " + paymentMethod;
            order.setRemark(remark);
            log.debug("更新订单 {} 备注: {}", order.getOrderNumber(), remark);

            Order paidOrder = orderRepository.save(order);
            log.info("订单 {} 状态已成功更新为 PAID 并保存。", paidOrder.getOrderNumber());

            // 统一支付成功后置处理
            handleOrderPaidSuccess(paidOrder.getId());

            return orderDtoAssembler.toPaymentResult(paidOrder);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("订单 {} 支付处理被中断", order.getOrderNumber(), e);
            throw new RuntimeException("支付处理中断");
        } catch (Exception e) {
            // 支付失败，统一更新订单状态为支付失败
            log.error("订单 {} 支付过程中发生意外错误，尝试将状态更新为 PAYMENT_FAILED: {}", order.getOrderNumber(), e.getMessage(), e);
            orderStatusUpdater.markPaymentFailed(order);
            orderRepository.save(order);
            // 支付失败不应该生成收益，也不需要发通知
            throw new RuntimeException("支付失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public OrderDTO confirmPayment(Long orderId) {
        log.info("管理员手动确认订单支付，ID: {}", orderId);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("订单不存在: " + orderId));

        // 简单校验：只有未支付的订单才能被手动确认支付
        if (order.getPaymentStatus() != PaymentStatus.UNPAID) {
            log.warn("订单 {} 的支付状态为 {}，不能手动确认支付", orderId, order.getPaymentStatus());
            throw new IllegalStateException("只有未支付的订单才能被确认支付");
        }

        // 统一更新订单状态
        orderStatusUpdater.markManualConfirmedPaid(order);
        // 可选：记录支付方式为手动确认
        if (order.getPaymentMethod() == null || order.getPaymentMethod().isEmpty()) {
            order.setPaymentMethod("MANUAL_CONFIRM");
        }
        // 可选：更新支付时间
        // order.setPaymentTime(LocalDateTime.now());

        Order updatedOrder = orderRepository.save(order);

        // 创建手动确认的支付记录，标记为不需要真实退款
        PaymentRecord manualPaymentRecord = new PaymentRecord();
        manualPaymentRecord.setOrderId(orderId);
        manualPaymentRecord.setPaymentMethod("MANUAL_CONFIRM");
        manualPaymentRecord.setOutTradeNo("MANUAL_" + orderId + "_" + System.currentTimeMillis());
        manualPaymentRecord.setAmount(order.getTotalAmount());
        manualPaymentRecord.setStatus("SUCCESS");
        manualPaymentRecord.setNeedRealRefund(false);
        paymentRecordRepository.save(manualPaymentRecord);
        log.info("订单 {} 已创建手动确认支付记录，不需要真实退款", orderId);
        log.info("订单 {} 已手动确认支付", orderId);

        // 统一支付成功后置处理
        handleOrderPaidSuccess(updatedOrder.getId());

        return orderDtoAssembler.toPaymentResult(updatedOrder);
    }

    @Override
    @Transactional
    public OrderDTO executeRefund(Long orderId, String reason) {
        log.info("管理员直接执行退款，订单ID: {}, 原因: {}", orderId, reason);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("订单不存在: " + orderId));

        // 检查当前状态
        if (order.getPaymentStatus() != PaymentStatus.PAID) {
            throw new IllegalStateException("只有已支付的订单才能执行退款");
        }

        // 检查是否已经在退款流程中
        if (OrderStatus.REFUND_PENDING.name().equals(order.getStatus()) ||
                OrderStatus.REFUNDED.name().equals(order.getStatus())) {
            throw new IllegalStateException("订单已在退款流程中，请勿重复操作");
        }

        // 设置退款相关信息
        User currentUser = getCurrentUser();
        order.setRefundType(RefundType.ADMIN_INITIATED);
        order.setRefundReason(reason != null && !reason.isEmpty() ? reason : "管理员直接退款");

        // 计算退款金额
        BigDecimal calculatedRefund = refundPolicyCalculator.calculate(order).amount();
        order.setRefundAmount(calculatedRefund);

        order.setRefundInitiatedBy(currentUser.getId());
        order.setRefundInitiatedAt(LocalDateTime.now());

        // 检查是否存在成功的支付记录
        java.util.Optional<PaymentRecord> paymentRecordOpt = paymentRecordRepository.findTopByOrderIdAndStatusOrderByCreatedAtDesc(orderId, "SUCCESS");

        String refundTradeNo = null;

        if (paymentRecordOpt.isPresent()) {
            PaymentRecord paymentRecord = paymentRecordOpt.get();
            // 检查是否需要真实退款（手动确认的订单不需要）
            if (Boolean.TRUE.equals(paymentRecord.getNeedRealRefund())) {
                // 需要真实退款
                RefundRequest refundRequest = RefundRequest.builder()
                        .orderId(order.getId())
                        .refundAmount(order.getRefundAmount())
                        .refundReason(order.getRefundReason())
                        .refundType(RefundType.ADMIN_INITIATED)
                        .build();
                RefundResponse refundResponse = paymentService.processRefund(refundRequest);
                if (!refundResponse.isSuccess()) {
                    throw new RuntimeException("退款失败: " + refundResponse.getMessage());
                }
                refundTradeNo = refundResponse.getRefundTradeNo();
            } else {
                // 不需要真实退款，直接模拟
                log.warn("订单 {} 的支付记录不需要真实退款，直接模拟退款成功", orderId);
                refundTradeNo = "MOCK_REFUND_" + System.currentTimeMillis();
            }
        } else {
            log.warn("订单 {} 没有支付记录，直接模拟退款成功", orderId);
            refundTradeNo = "MOCK_REFUND_" + System.currentTimeMillis();
        }

        // 统一更新为已退款状态
        orderStatusUpdater.markRefunded(order);
        order.setRefundTransactionId(refundTradeNo);
        order.setRefundProcessedBy(currentUser.getId());
        order.setRefundProcessedAt(LocalDateTime.now());

        // 添加退款记录到备注
        String refundNote = String.format("管理员直接退款 - 原因: %s, 退款金额: %s, 交易号: %s",
                reason != null ? reason : "管理员直接退款",
                calculatedRefund,
                refundTradeNo);
        if (order.getRemark() != null && !order.getRemark().isEmpty()) {
            order.setRemark(order.getRemark() + "\n" + refundNote);
        } else {
            order.setRemark(refundNote);
        }

        Order updatedOrder = orderRepository.save(order);

        // 释放优惠券
        try {
            couponService.releaseCoupons(order.getId());
        } catch (Exception e) {
            log.error("退款释放优惠券失败，订单: {}", order.getId(), e);
        }

        log.info("管理员直接执行退款完成，订单号: {}", order.getOrderNumber());

        // 发送退款完成通知
        try {
            if (order.getGuest() != null) {
                orderNotificationService.sendOrderRefundApprovedNotification(
                        order.getGuest().getId(),
                        order.getId(),
                        order.getOrderNumber(),
                        reason);
            }
        } catch (Exception e) {
            log.error("发送退款通知失败: {}", e.getMessage(), e);
        }

        return orderDtoAssembler.toPaymentResult(updatedOrder);
    }

    @Override
    @Transactional
    public OrderDTO approveRefund(Long orderId, String refundNote) {
        log.info("管理员批准退款申请，订单ID: {}, 备注: {}", orderId, refundNote);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("订单不存在: " + orderId));

        // 检查当前状态
        if (order.getPaymentStatus() != PaymentStatus.REFUND_PENDING) {
            throw new IllegalStateException("只有退款中的订单才能批准退款");
        }

        // 检查是否存在成功的支付记录
        java.util.Optional<PaymentRecord> paymentRecordOpt = paymentRecordRepository.findTopByOrderIdAndStatusOrderByCreatedAtDesc(orderId, "SUCCESS");

        RefundResponse refundResponse = null;
        String refundTradeNo = null;

        if (paymentRecordOpt.isPresent()) {
            PaymentRecord paymentRecord = paymentRecordOpt.get();
            // 检查是否需要真实退款（手动确认的订单不需要）
            if (Boolean.TRUE.equals(paymentRecord.getNeedRealRefund())) {
                // 需要真实退款，调用支付服务处理实际退款
                RefundRequest refundRequest = RefundRequest.builder()
                        .orderId(order.getId())
                        .refundAmount(order.getRefundAmount() != null ? order.getRefundAmount() : order.getTotalAmount())
                        .refundReason(order.getRefundReason())
                        .refundType(order.getRefundType())
                        .build();
                refundResponse = paymentService.processRefund(refundRequest);
                if (!refundResponse.isSuccess()) {
                    throw new RuntimeException("退款失败: " + refundResponse.getMessage());
                }
                refundTradeNo = refundResponse.getRefundTradeNo();
            } else {
                // 不需要真实退款（手动确认/测试订单），直接模拟退款成功
                log.warn("订单 {} 的支付记录不需要真实退款，直接模拟退款成功", orderId);
                refundTradeNo = "MOCK_REFUND_" + System.currentTimeMillis();
            }
        } else {
            // 没有支付记录（可能是管理员手动确认的测试订单），直接模拟退款成功
            log.warn("订单 {} 没有支付记录，直接模拟退款成功", orderId);
            refundTradeNo = "MOCK_REFUND_" + System.currentTimeMillis();
        }
        order.setRefundTransactionId(refundTradeNo);

        // 统一更新为已退款状态
        orderStatusUpdater.markRefunded(order);
        order.setRefundProcessedBy(getCurrentUser().getId());
        order.setRefundProcessedAt(LocalDateTime.now());

        // 添加退款备注
        if (refundNote != null && !refundNote.isEmpty()) {
            if (order.getRemark() != null && !order.getRemark().isEmpty()) {
                order.setRemark(order.getRemark() + "\n退款批准: " + refundNote);
            } else {
                order.setRemark("退款批准: " + refundNote);
            }
        }

        Order updatedOrder = orderRepository.save(order);
        log.info("订单 {} 退款已批准并完成", orderId);

        // 发送退款批准通知（处理用户/房源已删除的情况）
        try {
            if (order.getGuest() != null) {
                orderNotificationService.sendOrderRefundApprovedNotification(
                        order.getGuest().getId(),
                        order.getId(),
                        order.getOrderNumber(),
                        refundNote);
            } else {
                log.warn("订单 {} 的用户信息已删除，跳过发送退款批准通知", orderId);
            }
        } catch (Exception e) {
            log.error("发送退款批准通知失败: {}", e.getMessage(), e);
        }

        return orderDtoAssembler.toPaymentResult(updatedOrder);
    }

    @Override
    @Transactional
    public OrderDTO rejectRefund(Long orderId, String rejectReason) {
        log.info("管理员拒绝退款申请，订单ID: {}, 原因: {}", orderId, rejectReason);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("订单不存在: " + orderId));

        // 检查当前状态
        if (order.getPaymentStatus() != PaymentStatus.REFUND_PENDING) {
            throw new IllegalStateException("只有退款中的订单才能拒绝退款");
        }

        // 退款拒绝后统一恢复到已支付状态
        orderStatusUpdater.markRefundRejected(order);

        // 添加退款拒绝原因
        if (order.getRemark() != null && !order.getRemark().isEmpty()) {
            order.setRemark(order.getRemark() + "\n退款拒绝: " + rejectReason);
        } else {
            order.setRemark("退款拒绝: " + rejectReason);
        }
        order.setRefundRejectionReason(rejectReason);

        Order updatedOrder = orderRepository.save(order);
        log.info("订单 {} 退款申请被拒绝", orderId);

        // 发送退款拒绝通知
        try {
            orderNotificationService.sendOrderRefundRejectedNotification(
                    order.getGuest().getId(),
                    order.getId(),
                    order.getOrderNumber(),
                    rejectReason);
        } catch (Exception e) {
            log.error("发送退款拒绝通知失败: {}", e.getMessage(), e);
        }

        return orderDtoAssembler.toPaymentResult(updatedOrder);
    }

    @Override
    @Transactional
    public OrderDTO requestUserRefund(Long orderId, String reason) {
        log.info("用户申请退款，订单ID: {}, 退款原因: {}", orderId, reason);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("订单不存在: " + orderId));

        // 检查当前状态是否允许申请退款
        if (order.getPaymentStatus() != PaymentStatus.PAID) {
            throw new IllegalStateException("只有已支付的订单才能申请退款");
        }

        // 检查是否已经在退款流程中
        if (order.getStatus().equals(OrderStatus.REFUND_PENDING.name()) ||
                order.getStatus().equals(OrderStatus.REFUNDED.name())) {
            throw new IllegalStateException("订单已在退款流程中，请勿重复申请");
        }

        // 统一更新订单状态为退款中
        orderStatusUpdater.markRefundPending(order);

        // 设置退款相关信息
        User currentUser = getCurrentUser();
        order.setRefundType(RefundType.USER_REQUESTED);
        order.setRefundReason(reason != null && !reason.isEmpty() ? reason : "用户申请退款");

        // 固定本次申请金额，审批时沿用。
        BigDecimal calculatedRefund = refundPolicyCalculator.calculate(order).amount();
        order.setRefundAmount(calculatedRefund);

        order.setRefundInitiatedBy(currentUser.getId());
        order.setRefundInitiatedAt(LocalDateTime.now());

        // 添加退款申请记录到备注
        String refundNote = String.format("退款申请 - 类型: %s, 原因: %s, 申请人: %s, 计算退款金额: %s",
                RefundType.USER_REQUESTED.getDescription(),
                reason != null && !reason.isEmpty() ? reason : "用户申请退款",
                currentUser.getUsername(),
                calculatedRefund);
        if (order.getRemark() != null && !order.getRemark().isEmpty()) {
            order.setRemark(order.getRemark() + "\n" + refundNote);
        } else {
            order.setRemark(refundNote);
        }

        Order updatedOrder = orderRepository.save(order);

        // 发送退款申请通知给管理员
        try {
            log.info("退款申请已提交，订单号: {}, 等待管理员处理", order.getOrderNumber());
        } catch (Exception e) {
            log.error("发送退款申请通知失败: {}", e.getMessage(), e);
        }

        log.info("用户退款申请已提交，订单号: {}", order.getOrderNumber());
        return orderDtoAssembler.toPaymentResult(updatedOrder);
    }

    // 辅助方法：获取当前登录用户
    private User getCurrentUser() {
        return userRepository.findByUsername(
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName())
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在"));
    }

    @Override
    @Transactional
    public void handleOrderPaidSuccess(Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("支付成功后置处理：订单不存在，orderId={}", orderId);
            return;
        }
        if (!OrderStatus.PAID.name().equals(order.getStatus())) {
            log.warn("支付成功后置处理：订单状态不是PAID，orderId={}, status={}", orderId, order.getStatus());
            return;
        }

        // --- 核销优惠券与活动流水 ---
        try {
            var lockedCoupons = userCouponRepository.findByLockedOrderIdAndStatus(order.getId(), "LOCKED");
            couponService.useCoupons(order.getId());
            promotionUsageRepository.updateStatusByOrderId(order.getId(), "USED");
            log.info("订单 {} 优惠券与活动流水已核销。", order.getOrderNumber());
            // 转化漏斗埋点：核销
            try {
                for (var uc : lockedCoupons) {
                    if (uc.getTemplate() != null) {
                        couponAnalyticsService.track(uc.getTemplate().getId(), null, "ORDER_PAY", "USE",
                                order.getGuest() != null ? order.getGuest().getId() : null,
                                null, order.getId());
                    }
                }
            } catch (Exception trackEx) {
                log.warn("订单 {} 优惠券核销埋点失败: {}", order.getOrderNumber(), trackEx.getMessage());
            }
        } catch (Exception writeOffEx) {
            log.error("订单 {} 核销优惠券或活动流水失败: {}", order.getOrderNumber(), writeOffEx.getMessage(), writeOffEx);
        }
        // --- 核销结束 ---

        // --- 发送支付成功通知给房东和房客 ---
        try {
            User host = order.getHomestay().getOwner();
            User guest = order.getGuest();
            orderNotificationService.sendOrderPaymentSuccessNotification(
                    host.getId(), guest.getId(), order.getId(),
                    host.getUsername(), guest.getUsername(),
                    order.getHomestay().getTitle(),
                    order.getOrderNumber(),
                    order.getPaymentMethod() != null ? order.getPaymentMethod() : "UNKNOWN");
        } catch (Exception notifyEx) {
            log.error("发送订单 {} 支付成功通知失败: {}", order.getOrderNumber(), notifyEx.getMessage(), notifyEx);
        }
        // --- 通知发送结束 ---

        // --- 自动生成待结算收益记录 ---
        log.info("订单 {} 支付成功，准备调用 EarningService 生成收益记录...", order.getOrderNumber());
        try {
            EarningDTO generatedEarning = earningService.generatePendingEarningForOrder(order.getId());
            if (generatedEarning != null) {
                log.info("为订单 {} 成功生成或获取收益记录 ID: {}", order.getOrderNumber(), generatedEarning.getId());
            } else {
                log.warn("订单 {} 的收益记录生成调用返回为空，可能已存在或生成失败（详见 EarningService 日志）。", order.getOrderNumber());
            }
        } catch (Exception earningEx) {
            log.error("为订单 {} 调用 generatePendingEarningForOrder 时发生异常: {}", order.getOrderNumber(),
                    earningEx.getMessage(), earningEx);
        }
        log.info("EarningService 调用结束，订单 {} 的支付流程完成。", order.getOrderNumber());
        // --- 收益生成结束 ---
    }

    // 辅助方法：检查用户是否为订单的客户
    private boolean isOrderGuest(Order order, User user) {
        return order.getGuest() != null && order.getGuest().getId().equals(user.getId());
    }

}
