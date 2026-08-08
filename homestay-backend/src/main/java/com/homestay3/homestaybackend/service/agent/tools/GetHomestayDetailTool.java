package com.homestay3.homestaybackend.service.agent.tools;

import com.homestay3.homestaybackend.dto.HomestayDTO;
import com.homestay3.homestaybackend.service.HomestayQueryService;
import com.homestay3.homestaybackend.service.agent.AgentTool;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具：get_homestay_detail —— 房源详情（设施、政策、规则等；房东联系方式脱敏）
 */
public class GetHomestayDetailTool implements AgentTool {

    private final HomestayQueryService homestayQueryService;

    public GetHomestayDetailTool(HomestayQueryService homestayQueryService) {
        this.homestayQueryService = homestayQueryService;
    }

    @Override
    public String name() {
        return "get_homestay_detail";
    }

    @Override
    public String description() {
        return "查询房源详情：标题、设施（wifi/宠物等）、入住退房时间、取消政策、房屋规则、价格、位置。";
    }

    @Override
    public Map<String, String> argsDescription() {
        Map<String, String> args = new LinkedHashMap<>();
        args.put("homestayId", "房源ID（数字，必填）");
        return args;
    }

    @Override
    public Object execute(Map<String, Object> args, String username) {
        Long homestayId = ToolArgs.toLong(args.get("homestayId"));
        if (homestayId == null) {
            throw new IllegalArgumentException("缺少参数 homestayId");
        }
        HomestayDTO dto = homestayQueryService.getHomestayById(homestayId, null);
        if (dto != null) {
            dto.setOwnerPhone(null);
            dto.setOwnerEmail(null);
            dto.setOwnerRealName(null);
        }
        return dto;
    }
}
