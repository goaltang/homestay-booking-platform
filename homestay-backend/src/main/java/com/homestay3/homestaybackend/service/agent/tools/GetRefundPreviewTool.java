package com.homestay3.homestaybackend.service.agent.tools;

import com.homestay3.homestaybackend.repository.OrderRepository;
import com.homestay3.homestaybackend.service.OrderService;
import com.homestay3.homestaybackend.service.agent.AgentTool;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具：get_refund_preview —— 取消/退款能退多少钱（只读预览，不发起退款）
 */
public class GetRefundPreviewTool implements AgentTool {

    private final OrderService orderService;
    private final OrderRepository orderRepository;

    public GetRefundPreviewTool(OrderService orderService, OrderRepository orderRepository) {
        this.orderService = orderService;
        this.orderRepository = orderRepository;
    }

    @Override
    public String name() {
        return "get_refund_preview";
    }

    @Override
    public String description() {
        return "查询订单的退款预览：当前是否可退、可退金额、退款政策说明。只预览，不执行退款。";
    }

    @Override
    public Map<String, String> argsDescription() {
        Map<String, String> args = new LinkedHashMap<>();
        args.put("orderId", "订单ID（数字，必填）");
        return args;
    }

    @Override
    public Object execute(Map<String, Object> args, String username) {
        Long orderId = ToolArgs.toLong(args.get("orderId"));
        OrderAccessGuard.requireAccessibleOrder(orderRepository, orderId, username);
        return orderService.getRefundPreview(orderId);
    }
}
