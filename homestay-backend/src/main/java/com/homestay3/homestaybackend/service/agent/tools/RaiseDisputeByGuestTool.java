package com.homestay3.homestaybackend.service.agent.tools;

import com.homestay3.homestaybackend.entity.Order;
import com.homestay3.homestaybackend.repository.OrderRepository;
import com.homestay3.homestaybackend.service.agent.AgentTool;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具：raise_dispute_by_guest —— 代客发起争议（起草模式）
 * 本工具只起草不执行：校验订单归属后组装待确认提案（pendingAction），
 * 不调用 disputeService.raiseDisputeByGuest；真正执行由确认接口 /api/support/agent/confirm 完成，
 * 确认后订单进入 DISPUTE_PENDING，由管理员仲裁。
 */
public class RaiseDisputeByGuestTool implements AgentTool {

    private final OrderRepository orderRepository;

    public RaiseDisputeByGuestTool(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public String name() {
        return "raise_dispute_by_guest";
    }

    @Override
    public String description() {
        return "代客发起争议（本工具只起草不执行，返回待确认操作；用户确认后由确认接口执行）。仅在用户明确要求发起争议/申诉时调用。";
    }

    @Override
    public Map<String, String> argsDescription() {
        Map<String, String> args = new LinkedHashMap<>();
        args.put("orderId", "订单ID（数字，必填）");
        args.put("reason", "争议原因（字符串，必填）");
        return args;
    }

    @Override
    public Object execute(Map<String, Object> args, String username) {
        Long orderId = ToolArgs.toLong(args.get("orderId"));
        String reason = ToolArgs.toStr(args.get("reason"));
        Order order = OrderAccessGuard.requireAccessibleOrder(orderRepository, orderId, username);

        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> pendingAction = new LinkedHashMap<>();
        pendingAction.put("action", "raise_dispute_by_guest");
        pendingAction.put("orderId", orderId);
        pendingAction.put("reason", reason);
        pendingAction.put("summary", "将为您发起争议（订单 " + order.getOrderNumber() + "）");
        result.put("pendingAction", pendingAction);
        return result;
    }
}
