package com.homestay3.homestaybackend.service.agent.tools;

import com.homestay3.homestaybackend.service.ReviewService;
import com.homestay3.homestaybackend.service.agent.AgentTool;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具：get_review_stats —— 房源评价统计（评分、评价数等）
 */
public class GetReviewStatsTool implements AgentTool {

    private final ReviewService reviewService;

    public GetReviewStatsTool(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Override
    public String name() {
        return "get_review_stats";
    }

    @Override
    public String description() {
        return "查询房源的评价统计：平均评分、评价数量、各维度评分。";
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
        return reviewService.getHomestayReviewStats(homestayId);
    }
}
