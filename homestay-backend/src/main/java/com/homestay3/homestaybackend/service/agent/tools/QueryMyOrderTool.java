package com.homestay3.homestaybackend.service.agent.tools;

import com.homestay3.homestaybackend.dto.OrderDTO;
import com.homestay3.homestaybackend.entity.Order;
import com.homestay3.homestaybackend.repository.OrderRepository;
import com.homestay3.homestaybackend.service.OrderService;
import com.homestay3.homestaybackend.service.agent.AgentTool;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具：query_my_order —— 查询我的订单（状态/日期/金额/政策字段，脱敏后返回）
 */
public class QueryMyOrderTool implements AgentTool {

    private final OrderService orderService;
    private final OrderRepository orderRepository;

    public QueryMyOrderTool(OrderService orderService, OrderRepository orderRepository) {
        this.orderService = orderService;
        this.orderRepository = orderRepository;
    }

    @Override
    public String name() {
        return "query_my_order";
    }

    @Override
    public String description() {
        return "查询当前用户的订单状态、日期、金额与退款/争议政策信息。"
                + "用户提供订单ID或订单号时查指定订单；未提供时返回用户最近的订单列表。";
    }

    @Override
    public Map<String, String> argsDescription() {
        Map<String, String> args = new LinkedHashMap<>();
        args.put("orderId", "订单ID（数字，可选，与 orderNumber 二选一）");
        args.put("orderNumber", "订单号（字符串，可选，与 orderId 二选一）");
        return args;
    }

    @Override
    public Object execute(Map<String, Object> args, String username) {
        Long orderId = ToolArgs.toLong(args.get("orderId"));
        String orderNumber = ToolArgs.toStr(args.get("orderNumber"));

        if (orderId != null) {
            Order order = OrderAccessGuard.requireAccessibleOrder(orderRepository, orderId, username);
            return maskOrder(order);
        }
        if (orderNumber != null) {
            Order order = OrderAccessGuard.requireAccessibleOrderByNumber(orderRepository, orderNumber, username);
            return maskOrder(order);
        }

        Page<OrderDTO> page = orderService.getMyOrders(Map.of(), PageRequest.of(0, 5));
        List<Map<String, Object>> summaries = new ArrayList<>();
        if (page != null) {
            for (OrderDTO dto : page.getContent()) {
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("orderId", dto.getId());
                summary.put("orderNumber", dto.getOrderNumber());
                summary.put("homestayTitle", dto.getHomestayTitle());
                summary.put("status", dto.getStatus());
                summary.put("checkInDate", dto.getCheckInDate());
                summary.put("checkOutDate", dto.getCheckOutDate());
                summary.put("totalAmount", dto.getTotalAmount());
                summaries.add(summary);
            }
        }
        return Map.of("orders", summaries);
    }

    /**
     * 脱敏：只保留状态/日期/金额/政策字段；
     * 不返回 guestPhone、checkInCode、doorPassword、refundTransactionId 等敏感字段
     */
    private Map<String, Object> maskOrder(Order order) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", order.getId());
        result.put("orderNumber", order.getOrderNumber());
        result.put("homestayTitle", order.getHomestay() != null ? order.getHomestay().getTitle() : null);
        result.put("status", order.getStatus());
        result.put("paymentStatus", order.getPaymentStatus() != null ? order.getPaymentStatus().name() : null);
        result.put("checkInDate", order.getCheckInDate());
        result.put("checkOutDate", order.getCheckOutDate());
        result.put("nights", order.getNights());
        result.put("guestCount", order.getGuestCount());
        result.put("totalAmount", order.getTotalAmount());
        result.put("depositAmount", order.getDepositAmount());
        result.put("refundType", order.getRefundType() != null ? order.getRefundType().name() : null);
        result.put("refundReason", order.getRefundReason());
        result.put("refundAmount", order.getRefundAmount());
        result.put("refundRejectionReason", order.getRefundRejectionReason());
        result.put("disputeReason", order.getDisputeReason());
        result.put("disputeResolution", order.getDisputeResolution());
        result.put("disputeResolutionNote", order.getDisputeResolutionNote());
        return result;
    }
}
