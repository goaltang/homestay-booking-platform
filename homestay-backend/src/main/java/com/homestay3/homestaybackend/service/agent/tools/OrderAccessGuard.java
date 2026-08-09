package com.homestay3.homestaybackend.service.agent.tools;

import com.homestay3.homestaybackend.entity.Order;
import com.homestay3.homestaybackend.exception.AccessDeniedException;
import com.homestay3.homestaybackend.exception.ResourceNotFoundException;
import com.homestay3.homestaybackend.repository.OrderRepository;

/**
 * 订单访问权限校验（参考 CheckInServiceImpl.validateAccess 的思路）
 * 只有订单客人或该订单房源的房东才能访问订单相关数据
 */
public final class OrderAccessGuard {

    private OrderAccessGuard() {
    }

    /**
     * 按 orderId 加载订单并校验访问权限
     */
    static Order requireAccessibleOrder(OrderRepository orderRepository, Long orderId, String username) {
        if (orderId == null) {
            throw new IllegalArgumentException("缺少参数 orderId");
        }
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("订单不存在: " + orderId));
        checkAccess(order, username);
        return order;
    }

    /**
     * 按订单号加载订单并校验访问权限
     */
    static Order requireAccessibleOrderByNumber(OrderRepository orderRepository, String orderNumber, String username) {
        if (orderNumber == null || orderNumber.isBlank()) {
            throw new IllegalArgumentException("缺少参数 orderNumber");
        }
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("订单不存在: " + orderNumber));
        checkAccess(order, username);
        return order;
    }

    static void checkAccess(Order order, String username) {
        if (!isGuestOrHost(order, username)) {
            throw new AccessDeniedException("无权访问此订单");
        }
    }

    static boolean isGuestOrHost(Order order, String username) {
        if (order == null || username == null) {
            return false;
        }
        boolean isGuest = order.getGuest() != null && username.equals(order.getGuest().getUsername());
        boolean isHost = order.getHomestay() != null
                && order.getHomestay().getOwner() != null
                && username.equals(order.getHomestay().getOwner().getUsername());
        return isGuest || isHost;
    }

    /**
     * 按 orderId 加载订单并校验"必须是订单客人"权限（客人专属写操作专用，供确认执行接口使用）
     * 房东/无关用户均不允许，防止越权替他人操作
     */
    public static Order requireGuestOrder(OrderRepository orderRepository, Long orderId, String username) {
        Order order = requireAccessibleOrder(orderRepository, orderId, username);
        if (order.getGuest() == null || !username.equals(order.getGuest().getUsername())) {
            throw new AccessDeniedException("只有订单客人才能执行该操作");
        }
        return order;
    }
}
