package com.homestay3.homestaybackend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homestay3.homestaybackend.dto.AvailableCouponDTO;
import com.homestay3.homestaybackend.dto.CouponDiscountResult;
import com.homestay3.homestaybackend.entity.CouponTemplate;
import com.homestay3.homestaybackend.entity.Homestay;
import com.homestay3.homestaybackend.entity.UserCoupon;
import com.homestay3.homestaybackend.exception.ResourceNotFoundException;
import com.homestay3.homestaybackend.repository.CouponTemplateRepository;
import com.homestay3.homestaybackend.repository.HomestayRepository;
import com.homestay3.homestaybackend.repository.UserCouponRepository;
import com.homestay3.homestaybackend.service.CouponService;
import com.homestay3.homestaybackend.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CouponServiceImpl implements CouponService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final CouponTemplateRepository templateRepository;
    private final UserCouponRepository userCouponRepository;
    private final HomestayRepository homestayRepository;
    private final ObjectMapper objectMapper;
    private final SystemConfigService systemConfigService;

    @Override
    @Transactional
    public UserCoupon claimCoupon(Long userId, Long templateId) {
        CouponTemplate template = templateRepository.findByIdForUpdate(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("优惠券模板不存在"));

        normalizeTemplateDefaults(template);
        validateTemplateForSave(template);

        if (!"ACTIVE".equals(template.getStatus())) {
            throw new IllegalArgumentException("优惠券模板未激活");
        }

        // 领取渠道校验：专用券禁止手动领取
        if (Boolean.TRUE.equals(template.getIsNewUserCoupon())) {
            throw new IllegalArgumentException("该优惠券为新人专属券，不支持手动领取");
        }
        if (!"NONE".equals(template.getAutoIssueTrigger())) {
            throw new IllegalArgumentException("该优惠券为自动发放专用券，不支持手动领取");
        }

        LocalDateTime now = LocalDateTime.now();
        if (template.getValidStartAt() != null && template.getValidStartAt().isAfter(now)) {
            throw new IllegalArgumentException("优惠券未到领取时间");
        }
        if (template.getValidEndAt() != null && template.getValidEndAt().isBefore(now)) {
            throw new IllegalArgumentException("优惠券已过期");
        }

        if (template.getTotalStock() > 0 && template.getIssuedCount() >= template.getTotalStock()) {
            throw new IllegalArgumentException("优惠券已领完");
        }

        long userClaimedCount = userCouponRepository.countByUserIdAndTemplateId(userId, templateId);
        if (userClaimedCount >= template.getPerUserLimit()) {
            throw new IllegalArgumentException("已达到该优惠券领取上限");
        }

        LocalDateTime expireAt;
        if ("AFTER_CLAIM_DAYS".equals(template.getValidType())) {
            expireAt = now.plusDays(template.getValidDays());
        } else {
            expireAt = template.getValidEndAt();
        }

        if (expireAt != null && expireAt.isBefore(now)) {
            throw new IllegalArgumentException("优惠券已过期");
        }

        String couponCode = "CP" + System.currentTimeMillis()
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        UserCoupon userCoupon = UserCoupon.builder()
                .template(template)
                .userId(userId)
                .couponCode(couponCode)
                .status("AVAILABLE")
                .expireAt(expireAt != null ? expireAt : now.plusYears(1))
                // 保存领取快照，防止后续模板编辑追溯影响已发放券
                .snapshotFaceValue(template.getFaceValue())
                .snapshotDiscountRate(template.getDiscountRate())
                .snapshotThresholdAmount(template.getThresholdAmount())
                .snapshotMaxDiscount(template.getMaxDiscount())
                .snapshotScopeType(template.getScopeType())
                .snapshotScopeValueJson(template.getScopeValueJson())
                .snapshotSubsidyBearer(template.getSubsidyBearer())
                .build();

        if (template.getTotalStock() > 0) {
            int updated = templateRepository.incrementIssuedCount(templateId);
            if (updated == 0) {
                throw new IllegalArgumentException("优惠券库存不足");
            }
        }

        return userCouponRepository.save(userCoupon);
    }

    @Override
    @Transactional
    public List<UserCoupon> getAvailableCoupons(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        userCouponRepository.expireOutdatedCoupons(now);
        return userCouponRepository.findByUserIdAndStatusAndExpireAtAfterOrderByExpireAtAsc(
                userId, "AVAILABLE", now);
    }

    @Override
    @Transactional
    public List<AvailableCouponDTO> getAvailableCouponDTOs(Long userId) {
        return getAvailableCoupons(userId).stream()
                .map(this::toAvailableCouponDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<AvailableCouponDTO> getMyCoupons(Long userId, String status) {
        List<UserCoupon> coupons;
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            // 查询全部：可用 + 已使用 + 已过期
            LocalDateTime now = LocalDateTime.now();
            userCouponRepository.expireOutdatedCoupons(now);
            coupons = userCouponRepository.findByUserIdAndStatusOrderByExpireAtAsc(userId, "AVAILABLE");
            coupons.addAll(userCouponRepository.findByUserIdAndStatusOrderByExpireAtAsc(userId, "USED"));
            coupons.addAll(userCouponRepository.findByUserIdAndStatusOrderByExpireAtAsc(userId, "EXPIRED"));
        } else {
            // 按指定状态查询
            if ("AVAILABLE".equalsIgnoreCase(status)) {
                LocalDateTime now = LocalDateTime.now();
                userCouponRepository.expireOutdatedCoupons(now);
                coupons = userCouponRepository.findByUserIdAndStatusAndExpireAtAfterOrderByExpireAtAsc(
                        userId, "AVAILABLE", now);
            } else {
                coupons = userCouponRepository.findByUserIdAndStatusOrderByExpireAtAsc(userId, status.toUpperCase());
            }
        }
        return coupons.stream()
                .map(this::toAvailableCouponDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public CouponDiscountResult calculateCouponDiscount(Long userCouponId, Long homestayId, BigDecimal originalAmount) {
        return calculateCouponDiscountInternal(userCouponId, homestayId, originalAmount, null);
    }

    @Override
    @Transactional(readOnly = true)
    public CouponDiscountResult calculateCouponDiscount(List<Long> userCouponIds, Long homestayId,
                                                         BigDecimal originalAmount, Long userId) {
        if (userCouponIds == null || userCouponIds.isEmpty() || !isPositive(originalAmount)) {
            return zeroResult("PLATFORM");
        }

        List<CouponDiscountDetail> details = new ArrayList<>();
        for (Long couponId : userCouponIds) {
            CouponDiscountResult result = calculateCouponDiscountInternal(couponId, homestayId, originalAmount, userId);
            if (isPositive(result.getDiscountAmount())) {
                UserCoupon userCoupon = findUserCoupon(couponId, userId);
                if (userCoupon != null && userCoupon.getTemplate() != null) {
                    details.add(new CouponDiscountDetail(
                            couponId,
                            normalizeStackGroup(userCoupon.getTemplate().getStackGroup()),
                            result.getDiscountAmount(),
                            defaultMoney(result.getPlatformDiscountAmount()),
                            defaultMoney(result.getHostDiscountAmount())
                    ));
                }
            }
        }

        if (details.isEmpty()) {
            return zeroResult("PLATFORM");
        }

        Map<String, CouponDiscountDetail> bestByGroup = new LinkedHashMap<>();
        for (CouponDiscountDetail detail : details) {
            if ("DEFAULT".equals(detail.stackGroup())) {
                bestByGroup.put("DEFAULT_" + detail.couponId(), detail);
                continue;
            }

            CouponDiscountDetail existing = bestByGroup.get(detail.stackGroup());
            if (existing == null || detail.discount().compareTo(existing.discount()) > 0) {
                bestByGroup.put(detail.stackGroup(), detail);
            }
        }

        BigDecimal totalDiscount = BigDecimal.ZERO;
        BigDecimal platformDiscount = BigDecimal.ZERO;
        BigDecimal hostDiscount = BigDecimal.ZERO;
        List<Long> effectiveCouponIds = new ArrayList<>();

        for (CouponDiscountDetail detail : bestByGroup.values()) {
            totalDiscount = totalDiscount.add(detail.discount());
            platformDiscount = platformDiscount.add(detail.platformDiscount());
            hostDiscount = hostDiscount.add(detail.hostDiscount());
            effectiveCouponIds.add(detail.couponId());
        }

        BigDecimal maxAllowedDiscount = originalAmount.multiply(getMaxDiscountRate()).min(originalAmount);
        if (totalDiscount.compareTo(maxAllowedDiscount) > 0) {
            log.warn("优惠券总折扣超过上限，原始折扣={}, 上限={}, 已截断", totalDiscount, maxAllowedDiscount);
            BigDecimal ratio = maxAllowedDiscount.divide(totalDiscount, 8, RoundingMode.HALF_UP);
            platformDiscount = platformDiscount.multiply(ratio);
            hostDiscount = hostDiscount.multiply(ratio);
            totalDiscount = maxAllowedDiscount;
        }

        totalDiscount = money(totalDiscount);
        platformDiscount = money(platformDiscount);
        hostDiscount = money(hostDiscount);
        SplitDiscount adjusted = adjustSplitToTotal(platformDiscount, hostDiscount, totalDiscount);

        String finalBearer = resolveBearer(adjusted.platform(), adjusted.host());
        if (!isPositive(totalDiscount)) {
            effectiveCouponIds = Collections.emptyList();
        }

        return CouponDiscountResult.builder()
                .discountAmount(totalDiscount)
                .subsidyBearer(finalBearer)
                .platformDiscountAmount(adjusted.platform())
                .hostDiscountAmount(adjusted.host())
                .effectiveCouponIds(effectiveCouponIds)
                .build();
    }

    private UserCoupon findUserCoupon(Long couponId, Long userId) {
        if (userId != null) {
            return userCouponRepository.findByIdAndUserId(couponId, userId).orElse(null);
        }
        return userCouponRepository.findById(couponId).orElse(null);
    }

    private BigDecimal getMaxDiscountRate() {
        try {
            String value = systemConfigService.getConfigValue("coupon.max_discount.rate", "1.0");
            BigDecimal rate = new BigDecimal(value);
            if (rate.compareTo(BigDecimal.ZERO) < 0) {
                return BigDecimal.ZERO;
            }
            if (rate.compareTo(BigDecimal.ONE) > 0) {
                return BigDecimal.ONE;
            }
            return rate;
        } catch (Exception e) {
            log.warn("读取优惠券折扣上限配置失败，使用默认值 1.0: {}", e.getMessage());
            return BigDecimal.ONE;
        }
    }

    private record CouponDiscountDetail(Long couponId, String stackGroup, BigDecimal discount,
                                        BigDecimal platformDiscount, BigDecimal hostDiscount) {
    }

    private record SplitDiscount(BigDecimal platform, BigDecimal host) {
    }

    private CouponDiscountResult calculateCouponDiscountInternal(Long userCouponId, Long homestayId,
                                                                  BigDecimal originalAmount, Long userId) {
        if (!isPositive(originalAmount)) {
            return zeroResult("PLATFORM");
        }

        UserCoupon userCoupon = userId != null
                ? userCouponRepository.findByIdAndUserId(userCouponId, userId).orElse(null)
                : userCouponRepository.findById(userCouponId).orElse(null);

        if (userCoupon == null || !"AVAILABLE".equals(userCoupon.getStatus())) {
            return zeroResult("PLATFORM");
        }

        if (userCoupon.getExpireAt() != null && userCoupon.getExpireAt().isBefore(LocalDateTime.now())) {
            return zeroResult("PLATFORM");
        }

        if (!isApplicable(userCoupon, homestayId)) {
            return zeroResult("PLATFORM");
        }

        CouponTemplate template = resolveEffectiveTemplate(userCoupon);
        if (template == null) {
            return zeroResult("PLATFORM");
        }

        String bearer = normalizeBearer(template.getSubsidyBearer());
        if (template.getThresholdAmount() != null
                && originalAmount.compareTo(template.getThresholdAmount()) < 0) {
            return zeroResult(bearer);
        }

        BigDecimal discount = switch (template.getCouponType()) {
            case "CASH" -> defaultMoney(template.getFaceValue());
            case "DISCOUNT" -> calculateRateDiscount(template.getDiscountRate(), originalAmount);
            case "FULL_REDUCTION" -> template.getThresholdAmount() != null
                    && originalAmount.compareTo(template.getThresholdAmount()) >= 0
                    ? defaultMoney(template.getFaceValue())
                    : BigDecimal.ZERO;
            default -> BigDecimal.ZERO;
        };

        if (template.getMaxDiscount() != null && isPositive(template.getMaxDiscount())
                && discount.compareTo(template.getMaxDiscount()) > 0) {
            discount = template.getMaxDiscount();
        }

        discount = money(discount.min(originalAmount));
        if (!isPositive(discount)) {
            return zeroResult(bearer);
        }

        return buildResult(discount, bearer, List.of(userCouponId));
    }

    @Override
    @Transactional
    public void lockCoupons(List<Long> userCouponIds, Long orderId, Long userId) {
        if (userCouponIds == null || userCouponIds.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (Long couponId : userCouponIds.stream().distinct().toList()) {
            int updated = userCouponRepository.lockCoupon(couponId, userId, orderId, now);
            if (updated == 0) {
                throw new IllegalStateException("优惠券锁定失败，可能已被使用、过期或不属于当前用户，couponId=" + couponId);
            }
        }
    }

    @Override
    @Transactional
    public void releaseCoupons(Long orderId) {
        int released = userCouponRepository.releaseCouponsByOrderId(orderId);
        if (released > 0) {
            log.info("释放优惠券成功，订单ID={}, 数量={}", orderId, released);
        }
    }

    @Override
    @Transactional
    public void useCoupons(Long orderId) {
        int used = userCouponRepository.useCouponsByOrderId(orderId, LocalDateTime.now());
        if (used > 0) {
            log.info("核销优惠券成功，订单ID={}, 数量={}", orderId, used);
        }
    }

    @Override
    @Transactional
    public CouponTemplate createTemplate(CouponTemplate template) {
        normalizeTemplateDefaults(template);
        validateTemplateForSave(template);
        return templateRepository.save(template);
    }

    @Override
    public boolean isApplicable(UserCoupon userCoupon, Long homestayId) {
        CouponTemplate template = resolveEffectiveTemplate(userCoupon);
        if (template == null) {
            return false;
        }

        String scopeType = Optional.ofNullable(template.getScopeType()).orElse("ALL");
        if ("ALL".equals(scopeType)) {
            return true;
        }
        // fail-closed：非 ALL 范围必须有有效的 scopeValueJson
        if (template.getScopeValueJson() == null || template.getScopeValueJson().isBlank()) {
            return false;
        }
        if (homestayId == null) {
            return false;
        }

        Homestay homestay = homestayRepository.findById(homestayId).orElse(null);
        if (homestay == null) {
            return false;
        }
        try {
            JsonNode scopeValues = objectMapper.readTree(template.getScopeValueJson());
            return switch (scopeType) {
                case "HOMESTAY" -> {
                    for (JsonNode node : scopeValues) {
                        if (node.asLong() == homestayId) yield true;
                    }
                    yield false;
                }
                case "HOST" -> {
                    Long hostId = homestay.getOwner() != null ? homestay.getOwner().getId() : null;
                    for (JsonNode node : scopeValues) {
                        if (hostId != null && node.asLong() == hostId) yield true;
                    }
                    yield false;
                }
                case "CITY" -> {
                    for (JsonNode node : scopeValues) {
                        if (node.asText().equals(homestay.getCityCode())) yield true;
                    }
                    yield false;
                }
                case "TYPE" -> {
                    for (JsonNode node : scopeValues) {
                        if (node.asText().equals(homestay.getType())) yield true;
                    }
                    yield false;
                }
                case "GROUP" -> {
                    Long groupId = homestay.getGroup() != null ? homestay.getGroup().getId() : null;
                    for (JsonNode node : scopeValues) {
                        if (groupId != null && node.asLong() == groupId) yield true;
                    }
                    yield false;
                }
                // fail-closed：未知 scopeType 一律视为不适用
                default -> false;
            };
        } catch (Exception e) {
            log.warn("解析优惠券 scopeValueJson 失败: {}", e.getMessage());
            // fail-closed：解析失败视为不适用
            return false;
        }
    }

    private CouponDiscountResult zeroResult(String bearer) {
        return CouponDiscountResult.builder()
                .discountAmount(ZERO)
                .subsidyBearer(normalizeBearer(bearer))
                .platformDiscountAmount(ZERO)
                .hostDiscountAmount(ZERO)
                .effectiveCouponIds(Collections.emptyList())
                .build();
    }

    private CouponDiscountResult buildResult(BigDecimal discount, String bearer, List<Long> effectiveCouponIds) {
        BigDecimal normalizedDiscount = money(discount);
        SplitDiscount split = splitByBearer(normalizedDiscount, bearer);
        return CouponDiscountResult.builder()
                .discountAmount(normalizedDiscount)
                .subsidyBearer(resolveBearer(split.platform(), split.host()))
                .platformDiscountAmount(split.platform())
                .hostDiscountAmount(split.host())
                .effectiveCouponIds(effectiveCouponIds)
                .build();
    }

    private SplitDiscount splitByBearer(BigDecimal discount, String bearer) {
        String normalizedBearer = normalizeBearer(bearer);
        if ("HOST".equals(normalizedBearer)) {
            return new SplitDiscount(ZERO, money(discount));
        }
        if ("MIXED".equals(normalizedBearer)) {
            BigDecimal platform = money(discount.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP));
            return new SplitDiscount(platform, money(discount.subtract(platform)));
        }
        return new SplitDiscount(money(discount), ZERO);
    }

    private SplitDiscount adjustSplitToTotal(BigDecimal platformDiscount, BigDecimal hostDiscount,
                                             BigDecimal totalDiscount) {
        BigDecimal splitTotal = platformDiscount.add(hostDiscount);
        BigDecimal diff = totalDiscount.subtract(splitTotal);
        if (diff.compareTo(BigDecimal.ZERO) == 0) {
            return new SplitDiscount(platformDiscount, hostDiscount);
        }
        if (isPositive(hostDiscount)) {
            return new SplitDiscount(platformDiscount, money(hostDiscount.add(diff)));
        }
        return new SplitDiscount(money(platformDiscount.add(diff)), hostDiscount);
    }

    private String resolveBearer(BigDecimal platformDiscount, BigDecimal hostDiscount) {
        boolean hasPlatform = isPositive(platformDiscount);
        boolean hasHost = isPositive(hostDiscount);
        if (hasPlatform && hasHost) {
            return "MIXED";
        }
        if (hasHost) {
            return "HOST";
        }
        return "PLATFORM";
    }

    private BigDecimal calculateRateDiscount(BigDecimal discountRate, BigDecimal amount) {
        if (discountRate == null
                || discountRate.compareTo(BigDecimal.ZERO) <= 0
                || discountRate.compareTo(BigDecimal.ONE) >= 0) {
            return BigDecimal.ZERO;
        }
        return amount.multiply(BigDecimal.ONE.subtract(discountRate));
    }

    private void normalizeTemplateDefaults(CouponTemplate template) {
        if (template.getTotalStock() == null) template.setTotalStock(0);
        if (template.getIssuedCount() == null) template.setIssuedCount(0);
        if (template.getPerUserLimit() == null) template.setPerUserLimit(1);
        if (template.getValidType() == null || template.getValidType().isBlank()) template.setValidType("FIXED_TIME");
        if (template.getScopeType() == null || template.getScopeType().isBlank()) template.setScopeType("ALL");
        if (template.getSubsidyBearer() == null || template.getSubsidyBearer().isBlank()) template.setSubsidyBearer("PLATFORM");
        if (template.getStackGroup() == null || template.getStackGroup().isBlank()) template.setStackGroup("DEFAULT");
        if (template.getAutoIssueTrigger() == null || template.getAutoIssueTrigger().isBlank()) template.setAutoIssueTrigger("NONE");
        if (template.getStatus() == null || template.getStatus().isBlank()) template.setStatus("DRAFT");
        if (template.getIsNewUserCoupon() == null) template.setIsNewUserCoupon(false);
        if (template.getMaxDiscount() != null && template.getMaxDiscount().compareTo(BigDecimal.ZERO) <= 0) {
            template.setMaxDiscount(null);
        }
    }

    private void validateTemplateForSave(CouponTemplate template) {
        if (template.getName() == null || template.getName().isBlank()) {
            throw new IllegalArgumentException("优惠券名称不能为空");
        }
        if (!List.of("CASH", "DISCOUNT", "FULL_REDUCTION").contains(template.getCouponType())) {
            throw new IllegalArgumentException("优惠券类型不正确");
        }
        if (template.getTotalStock() < 0) {
            throw new IllegalArgumentException("总库存不能小于 0");
        }
        if (template.getIssuedCount() < 0) {
            throw new IllegalArgumentException("已发放数量不能小于 0");
        }
        if (template.getTotalStock() > 0 && template.getIssuedCount() > template.getTotalStock()) {
            throw new IllegalArgumentException("总库存不能小于已发放数量");
        }
        if (template.getPerUserLimit() < 1) {
            throw new IllegalArgumentException("每人限领必须大于 0");
        }
        if (template.getThresholdAmount() != null && template.getThresholdAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("使用门槛不能小于 0");
        }
        if (template.getValidStartAt() != null && template.getValidEndAt() != null
                && template.getValidStartAt().isAfter(template.getValidEndAt())) {
            throw new IllegalArgumentException("有效期开始时间不能晚于结束时间");
        }
        if ("AFTER_CLAIM_DAYS".equals(template.getValidType())
                && (template.getValidDays() == null || template.getValidDays() <= 0)) {
            throw new IllegalArgumentException("领取后有效天数必须大于 0");
        }
        if (!"AFTER_CLAIM_DAYS".equals(template.getValidType()) && template.getValidEndAt() == null) {
            throw new IllegalArgumentException("固定有效期优惠券必须设置结束时间");
        }
        if (!List.of("ALL", "HOMESTAY", "HOST", "CITY", "TYPE", "GROUP").contains(template.getScopeType())) {
            throw new IllegalArgumentException("适用范围类型不正确");
        }
        if (!List.of("NONE", "ORDER_COMPLETED", "BATCH_ISSUE", "REFERRAL").contains(template.getAutoIssueTrigger())) {
            throw new IllegalArgumentException("自动发放触发器类型不正确");
        }
        if ("DISCOUNT".equals(template.getCouponType())) {
            if (template.getDiscountRate() == null
                    || template.getDiscountRate().compareTo(BigDecimal.ZERO) <= 0
                    || template.getDiscountRate().compareTo(BigDecimal.ONE) >= 0) {
                throw new IllegalArgumentException("折扣券折扣率必须大于 0 且小于 1");
            }
            return;
        }
        if (!isPositive(template.getFaceValue())) {
            throw new IllegalArgumentException("现金券和满减券面值必须大于 0");
        }
        if ("FULL_REDUCTION".equals(template.getCouponType()) && !isPositive(template.getThresholdAmount())) {
            throw new IllegalArgumentException("满减券必须设置大于 0 的使用门槛");
        }
    }

    private String normalizeBearer(String bearer) {
        if ("HOST".equals(bearer) || "MIXED".equals(bearer)) {
            return bearer;
        }
        return "PLATFORM";
    }

    private String normalizeStackGroup(String stackGroup) {
        return stackGroup == null || stackGroup.isBlank() ? "DEFAULT" : stackGroup;
    }

    private BigDecimal defaultMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal money(BigDecimal value) {
        return defaultMoney(value).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private AvailableCouponDTO toAvailableCouponDTO(UserCoupon userCoupon) {
        CouponTemplate t = resolveEffectiveTemplate(userCoupon);
        return AvailableCouponDTO.builder()
                .id(userCoupon.getId())
                .name(t != null ? t.getName() : null)
                .couponType(t != null ? t.getCouponType() : null)
                .faceValue(t != null ? t.getFaceValue() : null)
                .discountRate(t != null ? t.getDiscountRate() : null)
                .thresholdAmount(t != null ? t.getThresholdAmount() : null)
                .maxDiscount(t != null ? t.getMaxDiscount() : null)
                .expireAt(userCoupon.getExpireAt())
                .scopeType(t != null ? t.getScopeType() : null)
                .subsidyBearer(t != null ? t.getSubsidyBearer() : null)
                .build();
    }

    /**
     * 解析用户优惠券的有效模板。
     * 如果 UserCoupon 保存了领取快照，则用快照字段覆盖原模板字段，
     * 防止后台修改模板后追溯影响已发放券。
     */
    private CouponTemplate resolveEffectiveTemplate(UserCoupon userCoupon) {
        CouponTemplate template = userCoupon.getTemplate();
        if (template == null) {
            return null;
        }
        // 没有快照时直接返回原模板
        if (userCoupon.getSnapshotFaceValue() == null
                && userCoupon.getSnapshotDiscountRate() == null
                && userCoupon.getSnapshotThresholdAmount() == null
                && userCoupon.getSnapshotMaxDiscount() == null
                && userCoupon.getSnapshotScopeType() == null
                && userCoupon.getSnapshotSubsidyBearer() == null) {
            return template;
        }
        // 有快照时创建副本，快照字段覆盖原字段
        CouponTemplate effective = new CouponTemplate();
        effective.setId(template.getId());
        effective.setName(template.getName());
        effective.setCouponType(template.getCouponType());
        effective.setFaceValue(firstNonNull(userCoupon.getSnapshotFaceValue(), template.getFaceValue()));
        effective.setDiscountRate(firstNonNull(userCoupon.getSnapshotDiscountRate(), template.getDiscountRate()));
        effective.setThresholdAmount(firstNonNull(userCoupon.getSnapshotThresholdAmount(), template.getThresholdAmount()));
        effective.setMaxDiscount(firstNonNull(userCoupon.getSnapshotMaxDiscount(), template.getMaxDiscount()));
        effective.setScopeType(firstNonNull(userCoupon.getSnapshotScopeType(), template.getScopeType()));
        effective.setScopeValueJson(firstNonNull(userCoupon.getSnapshotScopeValueJson(), template.getScopeValueJson()));
        effective.setSubsidyBearer(firstNonNull(userCoupon.getSnapshotSubsidyBearer(), template.getSubsidyBearer()));
        effective.setStackGroup(template.getStackGroup());
        effective.setValidType(template.getValidType());
        effective.setValidDays(template.getValidDays());
        effective.setValidStartAt(template.getValidStartAt());
        effective.setValidEndAt(template.getValidEndAt());
        effective.setTotalStock(template.getTotalStock());
        effective.setIssuedCount(template.getIssuedCount());
        effective.setPerUserLimit(template.getPerUserLimit());
        effective.setHostId(template.getHostId());
        effective.setIsNewUserCoupon(template.getIsNewUserCoupon());
        effective.setAutoIssueTrigger(template.getAutoIssueTrigger());
        effective.setStatus(template.getStatus());
        return effective;
    }

    private static <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }
}
