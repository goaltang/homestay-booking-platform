package com.homestay3.homestaybackend.service.agent.tools;

import com.homestay3.homestaybackend.repository.OrderRepository;
import com.homestay3.homestaybackend.service.CheckOutService;
import com.homestay3.homestaybackend.service.agent.AgentTool;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具：get_check_out_info —— 退房记录与押金状态查询（只读，绝不执行 processDeposit）
 */
public class GetCheckOutInfoTool implements AgentTool {

    private final CheckOutService checkOutService;
    private final OrderRepository orderRepository;

    public GetCheckOutInfoTool(CheckOutService checkOutService, OrderRepository orderRepository) {
        this.checkOutService = checkOutService;
        this.orderRepository = orderRepository;
    }

    @Override
    public String name() {
        return "get_check_out_info";
    }

    @Override
    public String description() {
        return "查询订单的退房记录：押金金额、押金状态（待退还/已扣押及原因）、额外费用、结算金额。";
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
        return checkOutService.getCheckOutRecord(orderId);
    }
}
