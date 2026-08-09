package com.homestay3.homestaybackend.service.impl;

import com.homestay3.homestaybackend.dto.OrderDTO;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 争议处理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DisputeServiceImpl implements DisputeService {

    private final OrderRepository orderRepository;
    private final PaymentProcessingService paymentProcessingService;
    private final OrderNotificationService orderNotificationService;
    private final DisputeRecordService disputeRecordService;
    private final UserRepository userRepository;
    private final OrderStatusUpdater orderStatusUpdater;

    /**
     * 终态订单状态集合：处于这些状态的订单已结束，不允许房客再发起争议
     */
    private static final Set<String> TERMINAL_STATUSES = Set.of(
            OrderStatus.REFUNDED.name(),
            OrderStatus.COMPLETED.name(),
            OrderStatus.CANCELLED.name(),
            OrderStatus.CANCELLED_BY_USER.name(),
            OrderStatus.CANCELLED_BY_HOST.name(),
            OrderStatus.CANCELLED_SYSTEM.name());

    @Override
    @Transactional
    public OrderDTO raiseDispute(Long orderId, String reason) {
        log.info("发起争议，订单ID: {}, 原因: {}", orderId, reason);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("订单不存在: " + orderId));

        // 检查当前状态 - 只能在退款中的订单发起争议
        if (order.getPaymentStatus() != PaymentStatus.REFUND_PENDING) {
            throw new IllegalStateException("只有退款中的订单才能发起争议");
        }

        // 检查是否已经在争议中
        if (order.getStatus().equals(OrderStatus.DISPUTE_PENDING.name())) {
            throw new IllegalStateException("订单已在争议处理中，请勿重复发起");
        }

        // 获取当前用户
        User currentUser = getCurrentUser();

        // 更新订单状态为争议待处理，同时更新支付状态为争议中
        orderStatusUpdater.markDisputePending(order, "用户发起争议: " + reason);

        // 设置争议相关信息
        order.setDisputeReason(reason);
        order.setDisputeRaisedBy(currentUser.getId());
        order.setDisputeRaisedAt(LocalDateTime.now());

        // 添加争议记录到备注
        String disputeNote = String.format("争议发起 - 原因: %s, 发起人: %s",
                reason, currentUser.getUsername());
        if (order.getRemark() != null && !order.getRemark().isEmpty()) {
            order.setRemark(order.getRemark() + "\n" + disputeNote);
        } else {
            order.setRemark(disputeNote);
        }

        Order updatedOrder = orderRepository.save(order);
        log.info("争议已发起，订单号: {}, 状态: {}", order.getOrderNumber(), updatedOrder.getStatus());

        // 创建争议记录
        disputeRecordService.createDisputeRecord(orderId, reason, currentUser.getId());

        // 发送争议发起通知
        try {
            if (order.getGuest() != null && order.getHomestay() != null) {
                orderNotificationService.sendDisputeRaisedNotification(
                        order.getId(),
                        order.getGuest().getId(),
                        order.getHomestay().getOwner() != null ? order.getHomestay().getOwner().getId() : null,
                        order.getOrderNumber(),
                        order.getHomestay().getTitle(),
                        reason);
            }
        } catch (Exception e) {
            log.error("发送争议发起通知失败: {}", e.getMessage(), e);
        }

        return convertToDTO(updatedOrder);
    }

    @Override
    @Transactional
    public OrderDTO raiseDisputeByGuest(Long orderId, String reason) {
        log.info("房客发起争议，订单ID: {}, 原因: {}", orderId, reason);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("订单不存在: " + orderId));

        // 状态校验：房客争议是"对已发生事实的申诉"（退款被拒/押金扣押/入住体验严重不符），
        // 不应被退款状态机锁死。因此只要订单不在争议中、且未进入终态（已退款/已完成/已取消系列），房客即可发起争议。
        if (order.getStatus().equals(OrderStatus.DISPUTE_PENDING.name())) {
            throw new IllegalStateException("订单已在争议处理中，请勿重复发起");
        }
        if (TERMINAL_STATUSES.contains(order.getStatus())) {
            throw new IllegalStateException("订单已结束，无法发起争议");
        }

        // 获取当前用户并校验必须是订单客人
        User currentUser = getCurrentUser();
        if (order.getGuest() == null || !order.getGuest().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("只有订单客人才能发起争议");
        }

        // 更新订单状态为争议待处理，同时更新支付状态为争议中
        orderStatusUpdater.markDisputePending(order, "房客发起争议: " + reason);

        // 设置争议相关信息
        order.setDisputeReason(reason);
        order.setDisputeRaisedBy(currentUser.getId());
        order.setDisputeRaisedAt(LocalDateTime.now());

        // 添加争议记录到备注
        String disputeNote = String.format("房客发起争议 - 原因: %s, 发起人: %s",
                reason, currentUser.getUsername());
        if (order.getRemark() != null && !order.getRemark().isEmpty()) {
            order.setRemark(order.getRemark() + "\n" + disputeNote);
        } else {
            order.setRemark(disputeNote);
        }

        Order updatedOrder = orderRepository.save(order);
        log.info("房客争议已发起，订单号: {}, 状态: {}", order.getOrderNumber(), updatedOrder.getStatus());

        // 创建争议记录
        disputeRecordService.createDisputeRecord(orderId, reason, currentUser.getId());

        // 发送争议发起通知
        try {
            if (order.getGuest() != null && order.getHomestay() != null) {
                orderNotificationService.sendDisputeRaisedNotification(
                        order.getId(),
                        order.getGuest().getId(),
                        order.getHomestay().getOwner() != null ? order.getHomestay().getOwner().getId() : null,
                        order.getOrderNumber(),
                        order.getHomestay().getTitle(),
                        reason);
            }
        } catch (Exception e) {
            log.error("发送争议发起通知失败: {}", e.getMessage(), e);
        }

        return convertToDTO(updatedOrder);
    }

    @Override
    @Transactional
    public OrderDTO resolveDispute(Long orderId, String resolution, String note) {
        log.info("解决争议，订单ID: {}, 仲裁结果: {}, 备注: {}", orderId, resolution, note);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("订单不存在: " + orderId));

        // 检查当前状态 - 只能在争议状态的订单解决争议
        if (!order.getStatus().equals(OrderStatus.DISPUTE_PENDING.name())) {
            throw new IllegalStateException("只有争议中的订单才能进行仲裁");
        }

        // 获取当前用户
        User currentUser = getCurrentUser();

        // 设置解决争议相关信息
        order.setDisputeResolution(resolution);
        order.setDisputeResolutionNote(note);
        order.setDisputeResolvedAt(LocalDateTime.now());

        // 添加仲裁记录到备注
        String resolutionText = "APPROVED".equals(resolution) ? "批准退款" : "拒绝退款（恢复订单）";
        String resolutionNote = String.format("争议仲裁 - 结果: %s, 仲裁人: %s, 备注: %s",
                resolutionText, currentUser.getUsername(), note != null ? note : "");
        if (order.getRemark() != null && !order.getRemark().isEmpty()) {
            order.setRemark(order.getRemark() + "\n" + resolutionNote);
        } else {
            order.setRemark(resolutionNote);
        }

        OrderDTO updatedOrderDTO;

        if ("APPROVED".equals(resolution)) {
            // 批准退款 - 调用退款批准服务
            updatedOrderDTO = paymentProcessingService.approveRefund(orderId, note);
            log.info("争议仲裁通过，订单 {} 已批准退款", orderId);

            // 更新争议记录
            disputeRecordService.resolveDisputeRecord(orderId, resolution, currentUser.getId(), note);

            // 发送争议解决通知
            try {
                if (order.getGuest() != null && order.getHomestay() != null) {
                    orderNotificationService.sendDisputeResolvedNotification(
                            order.getId(),
                            order.getGuest().getId(),
                            order.getHomestay().getOwner() != null ? order.getHomestay().getOwner().getId() : null,
                            order.getOrderNumber(),
                            order.getHomestay().getTitle(),
                            "APPROVED",
                            note);
                }
            } catch (Exception e) {
                log.error("发送争议解决通知失败: {}", e.getMessage(), e);
            }

            return updatedOrderDTO;
        } else {
            // 拒绝退款 - 恢复订单状态为已支付
            orderStatusUpdater.markPaid(order, "争议仲裁拒绝，恢复订单");
            order.setRefundProcessedBy(currentUser.getId());
            order.setRefundProcessedAt(LocalDateTime.now());

            Order updatedOrder = orderRepository.save(order);
            log.info("争议仲裁拒绝，订单 {} 已恢复为已支付状态", orderId);

            // 更新争议记录
            disputeRecordService.resolveDisputeRecord(orderId, resolution, currentUser.getId(), note);

            // 发送争议解决通知
            try {
                if (order.getGuest() != null && order.getHomestay() != null) {
                    orderNotificationService.sendDisputeResolvedNotification(
                            order.getId(),
                            order.getGuest().getId(),
                            order.getHomestay().getOwner() != null ? order.getHomestay().getOwner().getId() : null,
                            order.getOrderNumber(),
                            order.getHomestay().getTitle(),
                            "REJECTED",
                            note);
                }
            } catch (Exception e) {
                log.error("发送争议解决通知失败: {}", e.getMessage(), e);
            }

            return convertToDTO(updatedOrder);
        }
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));
    }

    private OrderDTO convertToDTO(Order order) {
        OrderDTO dto = new OrderDTO();
        dto.setId(order.getId());
        dto.setOrderNumber(order.getOrderNumber());
        dto.setStatus(order.getStatus());
        dto.setPaymentStatus(order.getPaymentStatus() != null ? order.getPaymentStatus().name() : null);
        dto.setTotalAmount(order.getTotalAmount());
        dto.setRemark(order.getRemark());

        // 争议相关字段
        dto.setDisputeReason(order.getDisputeReason());
        dto.setDisputeRaisedBy(order.getDisputeRaisedBy());
        dto.setDisputeRaisedAt(order.getDisputeRaisedAt());
        dto.setDisputeResolvedAt(order.getDisputeResolvedAt());
        dto.setDisputeResolution(order.getDisputeResolution());
        dto.setDisputeResolutionNote(order.getDisputeResolutionNote());

        // 退款相关字段
        dto.setRefundType(order.getRefundType() != null ? order.getRefundType().name() : null);
        dto.setRefundReason(order.getRefundReason());
        dto.setRefundAmount(order.getRefundAmount());
        dto.setRefundInitiatedBy(order.getRefundInitiatedBy());
        dto.setRefundInitiatedAt(order.getRefundInitiatedAt());
        dto.setRefundProcessedBy(order.getRefundProcessedBy());
        dto.setRefundProcessedAt(order.getRefundProcessedAt());
        dto.setRefundTransactionId(order.getRefundTransactionId());
        dto.setRefundRejectionReason(order.getRefundRejectionReason());

        return dto;
    }
}
