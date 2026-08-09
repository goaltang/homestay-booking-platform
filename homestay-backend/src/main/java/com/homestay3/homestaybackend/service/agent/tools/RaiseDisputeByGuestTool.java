package com.homestay3.homestaybackend.service.agent.tools;

import com.homestay3.homestaybackend.dto.OrderDTO;
import com.homestay3.homestaybackend.repository.OrderRepository;
import com.homestay3.homestaybackend.service.DisputeService;
import com.homestay3.homestaybackend.service.agent.AgentTool;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具：raise_dispute_by_guest —— 代客发起争议（申请型写操作）
 * 提交后订单进入 DISPUTE_PENDING，由管理员仲裁，agent 只代提申请。
 */
public class RaiseDisputeByGuestTool implements AgentTool {

    private final DisputeService disputeService;
    private final OrderRepository orderRepository;

    public RaiseDisputeByGuestTool(DisputeService disputeService, OrderRepository orderRepository) {
        this.disputeService = disputeService;
        this.orderRepository = orderRepository;
    }

    @Override
    public String name() {
        return "raise_dispute_by_guest";
    }

    @Override
    public String description() {
        return "代客发起争议（申请型操作，提交后由管理员仲裁）。仅在用户明确要求发起争议/申诉时调用。";
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
        OrderAccessGuard.requireAccessibleOrder(orderRepository, orderId, username);

        OrderDTO dto = disputeService.raiseDisputeByGuest(orderId, reason);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderNumber", dto.getOrderNumber());
        result.put("status", dto.getStatus());
        result.put("disputeReason", dto.getDisputeReason());
        return result;
    }
}
