package com.homestay3.homestaybackend.service.agent.tools;

import com.homestay3.homestaybackend.entity.Order;
import com.homestay3.homestaybackend.repository.OrderRepository;
import com.homestay3.homestaybackend.service.agent.AgentTool;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具：request_user_refund —— 代客申请退款（起草模式）
 * 本工具只起草不执行：校验订单归属后组装待确认提案（pendingAction），
 * 不调用 orderService.requestUserRefund；真正执行由确认接口 /api/support/agent/confirm 完成。
 * 审批权永远在人：agent 只组装材料，客人显式确认后才提交。
 */
public class RequestUserRefundTool implements AgentTool {

    private final OrderRepository orderRepository;

    public RequestUserRefundTool(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public String name() {
        return "request_user_refund";
    }

    @Override
    public String description() {
        return "代客申请退款（本工具只起草不执行，返回待确认操作；用户确认后由确认接口执行）。仅在用户明确要求申请退款时调用。";
    }

    @Override
    public Map<String, String> argsDescription() {
        Map<String, String> args = new LinkedHashMap<>();
        args.put("orderId", "订单ID（数字，必填）");
        args.put("reason", "退款原因（字符串，必填）");
        return args;
    }

    @Override
    public Object execute(Map<String, Object> args, String username) {
        Long orderId = ToolArgs.toLong(args.get("orderId"));
        String reason = ToolArgs.toStr(args.get("reason"));
        Order order = OrderAccessGuard.requireAccessibleOrder(orderRepository, orderId, username);

        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> pendingAction = new LinkedHashMap<>();
        pendingAction.put("action", "request_user_refund");
        pendingAction.put("orderId", orderId);
        pendingAction.put("reason", reason);
        pendingAction.put("summary", "将为订单 " + order.getOrderNumber() + " 申请退款");
        result.put("pendingAction", pendingAction);
        return result;
    }
}
