package com.homestay3.homestaybackend.service.agent.tools;

import com.homestay3.homestaybackend.repository.OrderRepository;
import com.homestay3.homestaybackend.service.CheckInService;
import com.homestay3.homestaybackend.service.agent.AgentTool;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具：get_check_in_info —— 入住码/入住凭证查询（仅订单客人或房东可查）
 */
public class GetCheckInInfoTool implements AgentTool {

    private final CheckInService checkInService;
    private final OrderRepository orderRepository;

    public GetCheckInInfoTool(CheckInService checkInService, OrderRepository orderRepository) {
        this.checkInService = checkInService;
        this.orderRepository = orderRepository;
    }

    @Override
    public String name() {
        return "get_check_in_info";
    }

    @Override
    public String description() {
        return "查询订单的入住凭证：入住码、门锁密码、密钥箱密码、位置说明、有效时间。";
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
        return checkInService.getCheckInCredential(orderId);
    }
}
