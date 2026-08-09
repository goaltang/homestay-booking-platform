package com.homestay3.homestaybackend.service.agent.tools;

import com.homestay3.homestaybackend.dto.OrderDTO;
import com.homestay3.homestaybackend.model.OrderStatus;
import com.homestay3.homestaybackend.repository.OrderRepository;
import com.homestay3.homestaybackend.service.OrderService;
import com.homestay3.homestaybackend.service.agent.AgentTool;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具：cancel_order_with_reason —— 代客取消订单（申请型写操作）
 * 按既有取消规则执行，cancelType 固定传 CANCELLED_BY_USER（agent 只代客人操作）。
 */
public class CancelOrderWithReasonTool implements AgentTool {

    private final OrderService orderService;
    private final OrderRepository orderRepository;

    public CancelOrderWithReasonTool(OrderService orderService, OrderRepository orderRepository) {
        this.orderService = orderService;
        this.orderRepository = orderRepository;
    }

    @Override
    public String name() {
        return "cancel_order_with_reason";
    }

    @Override
    public String description() {
        return "代客取消订单（申请型操作，按既有取消规则执行）。仅在用户明确要求取消订单时调用。";
    }

    @Override
    public Map<String, String> argsDescription() {
        Map<String, String> args = new LinkedHashMap<>();
        args.put("orderId", "订单ID（数字，必填）");
        args.put("reason", "取消原因（字符串，必填）");
        return args;
    }

    @Override
    public Object execute(Map<String, Object> args, String username) {
        Long orderId = ToolArgs.toLong(args.get("orderId"));
        String reason = ToolArgs.toStr(args.get("reason"));
        OrderAccessGuard.requireAccessibleOrder(orderRepository, orderId, username);

        OrderDTO dto = orderService.cancelOrderWithReason(orderId, OrderStatus.CANCELLED_BY_USER.name(), reason);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderNumber", dto.getOrderNumber());
        result.put("status", dto.getStatus());
        return result;
    }
}
