package com.homestay3.homestaybackend.service.agent;

import com.homestay3.homestaybackend.repository.OrderRepository;
import com.homestay3.homestaybackend.service.CheckInService;
import com.homestay3.homestaybackend.service.CheckOutService;
import com.homestay3.homestaybackend.service.HomestayQueryService;
import com.homestay3.homestaybackend.service.OrderService;
import com.homestay3.homestaybackend.service.PricingService;
import com.homestay3.homestaybackend.service.ReviewService;
import com.homestay3.homestaybackend.service.agent.tools.CalculatePriceTool;
import com.homestay3.homestaybackend.service.agent.tools.GetCheckInInfoTool;
import com.homestay3.homestaybackend.service.agent.tools.GetCheckOutInfoTool;
import com.homestay3.homestaybackend.service.agent.tools.GetHomestayDetailTool;
import com.homestay3.homestaybackend.service.agent.tools.GetRefundPreviewTool;
import com.homestay3.homestaybackend.service.agent.tools.GetReviewStatsTool;
import com.homestay3.homestaybackend.service.agent.tools.QueryMyOrderTool;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 工具注册表 —— 权限核心
 * 白名单硬编码：只允许第一层 FAQ Agent 的 7 个只读工具，
 * 构造函数内一次性写死，不开放任何动态注册入口。
 * 任何写操作（退款审批/押金/删除等禁区接口）在此注册表中不存在。
 */
@Service
public class AgentToolRegistry {

    private final Map<String, AgentTool> tools;

    public AgentToolRegistry(OrderService orderService,
                             CheckInService checkInService,
                             CheckOutService checkOutService,
                             HomestayQueryService homestayQueryService,
                             ReviewService reviewService,
                             PricingService pricingService,
                             OrderRepository orderRepository) {
        Map<String, AgentTool> registry = new LinkedHashMap<>();
        put(registry, new QueryMyOrderTool(orderService, orderRepository));
        put(registry, new GetRefundPreviewTool(orderService, orderRepository));
        put(registry, new GetCheckInInfoTool(checkInService, orderRepository));
        put(registry, new GetCheckOutInfoTool(checkOutService, orderRepository));
        put(registry, new GetHomestayDetailTool(homestayQueryService));
        put(registry, new GetReviewStatsTool(reviewService));
        put(registry, new CalculatePriceTool(pricingService));
        this.tools = Collections.unmodifiableMap(registry);
    }

    private static void put(Map<String, AgentTool> registry, AgentTool tool) {
        registry.put(tool.name(), tool);
    }

    /**
     * 工具清单（供决策 prompt 使用）
     */
    public List<String> toolSpecs() {
        List<String> specs = new ArrayList<>();
        for (AgentTool tool : tools.values()) {
            specs.add("- " + tool.name() + ": " + tool.description()
                    + " 参数: " + tool.argsDescription());
        }
        return specs;
    }

    public boolean contains(String toolName) {
        return toolName != null && tools.containsKey(toolName);
    }

    public int size() {
        return tools.size();
    }

    /**
     * 执行白名单内的工具
     *
     * @throws IllegalArgumentException 工具名不在白名单内
     */
    public Object execute(String toolName, Map<String, Object> args, String username) {
        AgentTool tool = tools.get(toolName);
        if (tool == null) {
            throw new IllegalArgumentException("工具未注册: " + toolName);
        }
        return tool.execute(args == null ? Map.of() : args, username);
    }
}
