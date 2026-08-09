package com.homestay3.homestaybackend.service.agent.tools;

import com.homestay3.homestaybackend.entity.Order;
import com.homestay3.homestaybackend.repository.OrderRepository;
import com.homestay3.homestaybackend.service.agent.AgentTool;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具：cancel_order_with_reason —— 代客取消订单（起草模式）
 * 本工具只起草不执行：校验订单归属后组装待确认提案（pendingAction），
 * 不调用 orderService.cancelOrderWithReason；真正执行由确认接口 /api/support/agent/confirm 完成，
 * 确认后 cancelType 固定传 CANCELLED_BY_USER（agent 只代客人操作）。
 */
public class CancelOrderWithReasonTool implements AgentTool {

    private final OrderRepository orderRepository;

    public CancelOrderWithReasonTool(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public String name() {
        return "cancel_order_with_reason";
    }

    @Override
    public String description() {
        return "代客取消订单（本工具只起草不执行，返回待确认操作；用户确认后由确认接口执行）。仅在用户明确要求取消订单时调用。";
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
        Order order = OrderAccessGuard.requireAccessibleOrder(orderRepository, orderId, username);

        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> pendingAction = new LinkedHashMap<>();
        pendingAction.put("action", "cancel_order_with_reason");
        pendingAction.put("orderId", orderId);
        pendingAction.put("reason", reason);
        pendingAction.put("summary", "将取消订单 " + order.getOrderNumber());
        result.put("pendingAction", pendingAction);
        return result;
    }
}
