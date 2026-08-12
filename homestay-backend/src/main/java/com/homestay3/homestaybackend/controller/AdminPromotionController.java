package com.homestay3.homestaybackend.controller;

import com.homestay3.homestaybackend.annotation.OperationLog;
import com.homestay3.homestaybackend.annotation.RateLimit;
import com.homestay3.homestaybackend.entity.CouponTemplate;
import com.homestay3.homestaybackend.entity.PromotionCampaign;
import com.homestay3.homestaybackend.entity.PromotionUsage;
import com.homestay3.homestaybackend.repository.CouponTemplateRepository;
import com.homestay3.homestaybackend.repository.PromotionCampaignRepository;
import com.homestay3.homestaybackend.repository.PromotionUsageRepository;
import com.homestay3.homestaybackend.service.CouponAnalyticsService;
import com.homestay3.homestaybackend.service.CouponBatchIssueService;
import com.homestay3.homestaybackend.service.CouponService;
import com.homestay3.homestaybackend.service.ReferralService;
import com.homestay3.homestaybackend.service.RoiAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin/promotions")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPromotionController {

    private final PromotionCampaignRepository campaignRepository;
    private final CouponTemplateRepository couponTemplateRepository;
    private final PromotionUsageRepository promotionUsageRepository;
    private final CouponService couponService;
    private final RoiAnalysisService roiAnalysisService;
    private final CouponBatchIssueService couponBatchIssueService;
    private final ReferralService referralService;
    private final CouponAnalyticsService couponAnalyticsService;

    @Value("${coupon.batch.mq-enabled:true}")
    private boolean mqEnabled;

    // ========== 活动管理 ==========

    @GetMapping("/campaigns")
    public ResponseEntity<?> getCampaigns(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String campaignType,
            @RequestParam(required = false) String subsidyBearer,
            @RequestParam(required = false) Long hostId) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        // 使用 Specification 动态查询
        org.springframework.data.jpa.domain.Specification<PromotionCampaign> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (name != null && !name.isEmpty()) {
                predicates.add(cb.like(root.get("name"), "%" + name + "%"));
            }
            if (status != null && !status.isEmpty()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (campaignType != null && !campaignType.isEmpty()) {
                predicates.add(cb.equal(root.get("campaignType"), campaignType));
            }
            if (subsidyBearer != null && !subsidyBearer.isEmpty()) {
                predicates.add(cb.equal(root.get("subsidyBearer"), subsidyBearer));
            }
            if (hostId != null) {
                predicates.add(cb.equal(root.get("hostId"), hostId));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        Page<PromotionCampaign> campaigns = campaignRepository.findAll(spec, pageable);
        return ResponseEntity.ok(campaigns);
    }

    @PostMapping("/campaigns")
    @OperationLog(operationType = "CREATE", resource = "PROMOTION", detail = "创建活动")
    public ResponseEntity<?> createCampaign(@RequestBody PromotionCampaign campaign) {
        try {
            // 设置规则的级联回引用
            if (campaign.getRules() != null) {
                campaign.getRules().forEach(rule -> rule.setCampaign(campaign));
            }
            PromotionCampaign saved = campaignRepository.save(campaign);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/campaigns/{id}")
    @OperationLog(operationType = "UPDATE", resource = "PROMOTION", resourceId = "#id", detail = "更新活动")
    public ResponseEntity<?> updateCampaign(@PathVariable Long id, @RequestBody PromotionCampaign campaign) {
        Optional<PromotionCampaign> existingOpt = campaignRepository.findById(id);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        PromotionCampaign existing = existingOpt.get();
        // 清除旧规则，前端传入新规则
        existing.getRules().clear();
        campaignRepository.flush();

        existing.setName(campaign.getName());
        existing.setCampaignType(campaign.getCampaignType());
        existing.setStartAt(campaign.getStartAt());
        existing.setEndAt(campaign.getEndAt());
        existing.setPriority(campaign.getPriority());
        existing.setStackable(campaign.getStackable());
        existing.setBudgetTotal(campaign.getBudgetTotal());
        existing.setSubsidyBearer(campaign.getSubsidyBearer());
        existing.setBudgetAlertThreshold(campaign.getBudgetAlertThreshold());

        if (campaign.getRules() != null) {
            for (var rule : campaign.getRules()) {
                rule.setCampaign(existing);
                existing.getRules().add(rule);
            }
        }

        PromotionCampaign updated = campaignRepository.save(existing);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/campaigns/{id}/publish")
    @OperationLog(operationType = "UPDATE", resource = "PROMOTION", resourceId = "#id", detail = "发布活动")
    public ResponseEntity<?> publishCampaign(@PathVariable Long id) {
        Optional<PromotionCampaign> opt = campaignRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        PromotionCampaign campaign = opt.get();
        campaign.setStatus("ACTIVE");
        campaignRepository.save(campaign);
        return ResponseEntity.ok(Map.of("message", "活动已发布"));
    }

    @PostMapping("/campaigns/{id}/pause")
    @OperationLog(operationType = "UPDATE", resource = "PROMOTION", resourceId = "#id", detail = "暂停活动")
    public ResponseEntity<?> pauseCampaign(@PathVariable Long id) {
        Optional<PromotionCampaign> opt = campaignRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        PromotionCampaign campaign = opt.get();
        campaign.setStatus("PAUSED");
        campaignRepository.save(campaign);
        return ResponseEntity.ok(Map.of("message", "活动已暂停"));
    }

    @DeleteMapping("/campaigns/{id}")
    @OperationLog(operationType = "DELETE", resource = "PROMOTION", resourceId = "#id", detail = "删除活动")
    public ResponseEntity<?> deleteCampaign(@PathVariable Long id) {
        Optional<PromotionCampaign> opt = campaignRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        PromotionCampaign campaign = opt.get();
        if (!"DRAFT".equals(campaign.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("error", "只有草稿状态的活动可以删除"));
        }
        campaignRepository.delete(campaign);
        return ResponseEntity.ok(Map.of("message", "活动已删除"));
    }

    // ========== 优惠券模板管理 ==========

    @GetMapping("/templates")
    public ResponseEntity<?> getCouponTemplates(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String subsidyBearer) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        org.springframework.data.jpa.domain.Specification<CouponTemplate> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (name != null && !name.isEmpty()) {
                predicates.add(cb.like(root.get("name"), "%" + name + "%"));
            }
            if (status != null && !status.isEmpty()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (subsidyBearer != null && !subsidyBearer.isEmpty()) {
                predicates.add(cb.equal(root.get("subsidyBearer"), subsidyBearer));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        Page<CouponTemplate> templates = couponTemplateRepository.findAll(spec, pageable);
        return ResponseEntity.ok(templates);
    }

    @PostMapping("/templates")
    @OperationLog(operationType = "CREATE", resource = "PROMOTION", detail = "创建优惠券模板")
    public ResponseEntity<?> createTemplate(@RequestBody CouponTemplate template) {
        try {
            CouponTemplate saved = couponService.createTemplate(template);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/templates/{id}")
    public ResponseEntity<?> updateTemplate(@PathVariable Long id, @RequestBody CouponTemplate template) {
        try {
            Optional<CouponTemplate> existing = couponTemplateRepository.findById(id);
            if (existing.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            CouponTemplate target = existing.get();

            // 安全校验：ACTIVE 且已发放的模板，禁止修改关键计价字段
            if ("ACTIVE".equals(target.getStatus()) && target.getIssuedCount() != null && target.getIssuedCount() > 0) {
                // 只允许修改白名单字段：name, validStartAt, validEndAt, status, totalStock, perUserLimit
                // 其他关键字段保留原值
                target.setName(template.getName());
                target.setValidStartAt(template.getValidStartAt());
                target.setValidEndAt(template.getValidEndAt());
                target.setStatus(template.getStatus());
                target.setTotalStock(template.getTotalStock());
                target.setPerUserLimit(template.getPerUserLimit());
                // 禁止修改：couponType, faceValue, discountRate, thresholdAmount, maxDiscount, scopeType, scopeValueJson, subsidyBearer, isNewUserCoupon, autoIssueTrigger, stackGroup, hostId, validDays, validType
            } else {
                copyTemplateEditableFields(target, template);
            }

            CouponTemplate updated = couponService.createTemplate(target);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/templates/{id}")
    public ResponseEntity<?> deleteTemplate(@PathVariable Long id) {
        Optional<CouponTemplate> opt = couponTemplateRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        CouponTemplate template = opt.get();
        if (!"DRAFT".equals(template.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("error", "只有草稿状态的模板可以删除"));
        }
        couponTemplateRepository.delete(template);
        return ResponseEntity.ok(Map.of("message", "模板已删除"));
    }

    // ========== 使用流水与统计 ==========

    @GetMapping("/usages")
    public ResponseEntity<?> getUsages(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<PromotionUsage> usages = promotionUsageRepository.findAll(pageable);
        return ResponseEntity.ok(usages);
    }

    @GetMapping("/statistics")
    public ResponseEntity<?> getStatistics(
            @RequestParam(required = false) String campaignType,
            @RequestParam(required = false) String subsidyBearer,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        List<PromotionUsage> usages = promotionUsageRepository.findAll();

        // 按 campaignId 分组统计
        Map<Long, PromotionCampaign> campaignMap = new HashMap<>();
        for (PromotionUsage usage : usages) {
            if (usage.getCampaignId() != null && !campaignMap.containsKey(usage.getCampaignId())) {
                campaignRepository.findById(usage.getCampaignId()).ifPresent(c -> campaignMap.put(c.getId(), c));
            }
        }

        // 过滤
        List<PromotionUsage> filtered = usages.stream().filter(u -> {
            if (u.getCampaignId() != null) {
                PromotionCampaign c = campaignMap.get(u.getCampaignId());
                if (c != null) {
                    if (campaignType != null && !campaignType.isEmpty() && !campaignType.equals(c.getCampaignType())) {
                        return false;
                    }
                    if (subsidyBearer != null && !subsidyBearer.isEmpty() && !subsidyBearer.equals(c.getSubsidyBearer())) {
                        return false;
                    }
                }
            }
            if (u.getCreatedAt() != null) {
                if (startDate != null && !startDate.isEmpty() && u.getCreatedAt().isBefore(LocalDateTime.parse(startDate + "T00:00:00"))) {
                    return false;
                }
                if (endDate != null && !endDate.isEmpty() && u.getCreatedAt().isAfter(LocalDateTime.parse(endDate + "T23:59:59"))) {
                    return false;
                }
            }
            return true;
        }).toList();

        BigDecimal totalDiscount = filtered.stream()
                .map(PromotionUsage::getDiscountAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long usedCount = filtered.stream().filter(u -> "USED".equals(u.getStatus())).count();

        // 承担方拆分
        BigDecimal platformDiscount = filtered.stream()
                .filter(u -> "PLATFORM".equals(u.getBearer()) || "MIXED".equals(u.getBearer()))
                .map(PromotionUsage::getDiscountAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal hostDiscount = filtered.stream()
                .filter(u -> "HOST".equals(u.getBearer()) || "MIXED".equals(u.getBearer()))
                .map(PromotionUsage::getDiscountAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return ResponseEntity.ok(Map.of(
                "totalDiscount", totalDiscount,
                "totalUsageCount", filtered.size(),
                "usedCount", usedCount,
                "platformDiscount", platformDiscount,
                "hostDiscount", hostDiscount,
                "campaignCount", campaignMap.values().stream().distinct().count()
        ));
    }

    // ========== ROI 分析 ==========

    @GetMapping("/roi/overview")
    public ResponseEntity<?> getRoiOverview(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        LocalDateTime start = parseDateTime(startDate, true);
        LocalDateTime end = parseDateTime(endDate, false);
        return ResponseEntity.ok(roiAnalysisService.getPlatformRoiOverview(start, end));
    }

    @GetMapping("/roi/campaigns")
    public ResponseEntity<?> getRoiCampaigns(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "10") int limit) {
        LocalDateTime start = parseDateTime(startDate, true);
        LocalDateTime end = parseDateTime(endDate, false);
        return ResponseEntity.ok(roiAnalysisService.getPlatformCampaignRoi(start, end, limit));
    }

    // ========== 批量发券 ==========

    @PostMapping("/batch-tasks")
    @RateLimit(limit = 5, windowSeconds = 60, key = "admin:coupon-batch")
    public ResponseEntity<?> createBatchTask(@RequestBody Map<String, Object> request) {
        try {
            Long templateId = Long.valueOf(request.get("templateId").toString());
            String name = (String) request.get("name");
            String filterType = (String) request.get("filterType");
            @SuppressWarnings("unchecked")
            Map<String, Object> filterParams = (Map<String, Object>) request.getOrDefault("filterParams", Map.of());
            // MQ 开启：创建任务后由 MQ 消费者驱动执行；关闭：走 @Async 降级路径
            var task = couponBatchIssueService.createBatchTask(templateId, name, filterType, filterParams, null);
            if (!mqEnabled) {
                couponBatchIssueService.executeBatchTaskAsync(task.getId());
            }
            return ResponseEntity.ok(Map.of("taskId", task.getId(), "totalCount", task.getTotalCount(), "message", "任务已创建并执行"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/batch-tasks")
    public ResponseEntity<?> getBatchTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(couponBatchIssueService.getTaskList(status, pageable));
    }

    @GetMapping("/batch-tasks/{id}")
    public ResponseEntity<?> getBatchTaskDetail(@PathVariable Long id) {
        return ResponseEntity.ok(couponBatchIssueService.getTaskDetail(id));
    }

    @GetMapping("/batch-tasks/{id}/items")
    public ResponseEntity<?> getBatchTaskItems(
            @PathVariable Long id,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(couponBatchIssueService.getTaskItems(id, status, pageable));
    }

    @PostMapping("/batch-tasks/{id}/retry-failed")
    public ResponseEntity<?> retryFailedItems(@PathVariable Long id) {
        try {
            couponBatchIssueService.retryFailedItems(id);
            return ResponseEntity.ok(Map.of("message", "已开始重试失败项"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ========== 邀请裂变 ==========

    @PostMapping("/referral-codes")
    public ResponseEntity<?> generateReferralCode(@RequestBody Map<String, Object> request) {
        try {
            Long inviterId = Long.valueOf(request.get("inviterId").toString());
            Long templateIdForInvitee = request.get("templateIdForInvitee") != null
                    ? Long.valueOf(request.get("templateIdForInvitee").toString()) : null;
            Long templateIdForInviter = request.get("templateIdForInviter") != null
                    ? Long.valueOf(request.get("templateIdForInviter").toString()) : null;
            Integer maxUses = request.get("maxUses") != null ? Integer.valueOf(request.get("maxUses").toString()) : 1;
            Integer validDays = request.get("validDays") != null ? Integer.valueOf(request.get("validDays").toString()) : 30;
            return ResponseEntity.ok(referralService.generateReferralCode(inviterId, templateIdForInvitee, templateIdForInviter, maxUses, validDays));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ========== 转化漏斗 & 渠道分析 ==========

    @GetMapping("/analytics/funnel")
    public ResponseEntity<?> getCouponFunnel(
            @RequestParam Long templateId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        LocalDateTime start = parseDateTime(startDate, true);
        LocalDateTime end = parseDateTime(endDate, false);
        if (start == null) start = LocalDateTime.now().minusDays(30);
        if (end == null) end = LocalDateTime.now();
        return ResponseEntity.ok(couponAnalyticsService.getCouponFunnel(templateId, start, end));
    }

    @GetMapping("/analytics/channels")
    public ResponseEntity<?> getChannelStats(
            @RequestParam Long templateId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        LocalDateTime start = parseDateTime(startDate, true);
        LocalDateTime end = parseDateTime(endDate, false);
        if (start == null) start = LocalDateTime.now().minusDays(30);
        if (end == null) end = LocalDateTime.now();
        return ResponseEntity.ok(couponAnalyticsService.getChannelStats(templateId, start, end));
    }

    private void copyTemplateEditableFields(CouponTemplate target, CouponTemplate source) {
        target.setName(source.getName());
        target.setCouponType(source.getCouponType());
        target.setFaceValue(source.getFaceValue());
        target.setDiscountRate(source.getDiscountRate());
        target.setThresholdAmount(source.getThresholdAmount());
        target.setMaxDiscount(source.getMaxDiscount());
        target.setTotalStock(source.getTotalStock());
        target.setPerUserLimit(source.getPerUserLimit());
        target.setValidType(source.getValidType());
        target.setValidDays(source.getValidDays());
        target.setValidStartAt(source.getValidStartAt());
        target.setValidEndAt(source.getValidEndAt());
        target.setScopeType(source.getScopeType());
        target.setScopeValueJson(source.getScopeValueJson());
        target.setSubsidyBearer(source.getSubsidyBearer());
        target.setHostId(source.getHostId());
        target.setIsNewUserCoupon(source.getIsNewUserCoupon());
        target.setAutoIssueTrigger(source.getAutoIssueTrigger());
        target.setStackGroup(source.getStackGroup());
        target.setStatus(source.getStatus());
    }

    private LocalDateTime parseDateTime(String dateStr, boolean startOfDay) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        return startOfDay
                ? LocalDateTime.parse(dateStr + "T00:00:00")
                : LocalDateTime.parse(dateStr + "T23:59:59");
    }
}
