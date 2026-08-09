package com.homestay3.homestaybackend.service.agent.tools;

import com.homestay3.homestaybackend.dto.OrderDTO;
import com.homestay3.homestaybackend.repository.OrderRepository;
import com.homestay3.homestaybackend.service.OrderService;
import com.homestay3.homestaybackend.service.agent.AgentTool;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具：request_user_refund —— 代客发起退款申请（申请型写操作）
 * 提交后由房东/管理员审批，agent 只代提申请，不做审批。
 */
public class RequestUserRefundTool implements AgentTool {

    private final OrderService orderService;
    private final OrderRepository orderRepository;

    public RequestUserRefundTool(OrderService orderService, OrderRepository orderRepository) {
        this.orderService = orderService;
        this.orderRepository = orderRepository;
    }

    @Override
    public String name() {
        return "request_user_refund";
    }

    @Override
    public String description() {
        return "代客发起退款申请（申请型操作，提交后由房东/管理员审批）。仅在用户明确要求申请退款时调用。";
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
        OrderAccessGuard.requireAccessibleOrder(orderRepository, orderId, username);

        OrderDTO dto = orderService.requestUserRefund(orderId, reason);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderNumber", dto.getOrderNumber());
        result.put("status", dto.getStatus());
        result.put("paymentStatus", dto.getPaymentStatus());
        result.put("refundAmount", dto.getRefundAmount());
        result.put("refundReason", dto.getRefundReason());
        return result;
    }
}
