package com.homestay3.homestaybackend.service.agent.tools;

import com.homestay3.homestaybackend.dto.PricingResult;
import com.homestay3.homestaybackend.service.PricingService;
import com.homestay3.homestaybackend.service.agent.AgentTool;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具：calculate_price —— 按日期试算房价（走统一计价服务，报价与下单一致）
 */
public class CalculatePriceTool implements AgentTool {

    private final PricingService pricingService;

    public CalculatePriceTool(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    @Override
    public String name() {
        return "calculate_price";
    }

    @Override
    public String description() {
        return "按入住/退房日期试算房源总价：房费、折扣、清洁费、服务费、应付总额。";
    }

    @Override
    public Map<String, String> argsDescription() {
        Map<String, String> args = new LinkedHashMap<>();
        args.put("homestayId", "房源ID（数字，必填）");
        args.put("checkInDate", "入住日期（yyyy-MM-dd，必填）");
        args.put("checkOutDate", "退房日期（yyyy-MM-dd，必填）");
        args.put("guestCount", "入住人数（数字，可选，默认2）");
        return args;
    }

    @Override
    public Object execute(Map<String, Object> args, String username) {
        Long homestayId = ToolArgs.toLong(args.get("homestayId"));
        LocalDate checkInDate = ToolArgs.toDate(args.get("checkInDate"));
        LocalDate checkOutDate = ToolArgs.toDate(args.get("checkOutDate"));
        Integer guestCount = ToolArgs.toInt(args.get("guestCount"));

        if (homestayId == null) {
            throw new IllegalArgumentException("缺少参数 homestayId");
        }
        if (checkInDate == null || checkOutDate == null) {
            throw new IllegalArgumentException("缺少参数 checkInDate 或 checkOutDate");
        }
        if (guestCount == null) {
            guestCount = 2;
        }

        PricingResult result = pricingService.calculate(homestayId, checkInDate, checkOutDate,
                guestCount, null, null);
        if (result != null) {
            result.setHostReceivableAmount(null);
        }
        return result;
    }
}
