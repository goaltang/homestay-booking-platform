package com.homestay3.homestaybackend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homestay3.homestaybackend.dto.AppliedPricingRuleDTO;
import com.homestay3.homestaybackend.dto.PriceCalculationResponse;
import com.homestay3.homestaybackend.entity.Homestay;
import com.homestay3.homestaybackend.entity.HolidayCalendar;
import com.homestay3.homestaybackend.entity.PricingRule;
import com.homestay3.homestaybackend.repository.HolidayCalendarRepository;
import com.homestay3.homestaybackend.repository.PricingRuleRepository;
import com.homestay3.homestaybackend.service.PricingEngineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PricingEngineServiceImpl implements PricingEngineService {

    private final HolidayCalendarRepository holidayCalendarRepository;
    private final PricingRuleRepository pricingRuleRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public List<PriceCalculationResponse.DailyPrice> calculateDailyPrices(
            Homestay homestay, LocalDate checkInDate, LocalDate checkOutDate) {

        List<PriceCalculationResponse.DailyPrice> dailyPrices = new ArrayList<>();
        BigDecimal basePricePerNight = homestay.getPrice();
        int nights = (int) ChronoUnit.DAYS.between(checkInDate, checkOutDate);
        int advanceDays = (int) ChronoUnit.DAYS.between(LocalDate.now(), checkInDate);

        // 预加载所有启用的规则，避免循环中多次查询
        List<PricingRule> allRules = pricingRuleRepository.findByEnabledTrueOrderByPriorityAsc();

        // 批量查询日期范围内的节假日，避免 N+1
        List<HolidayCalendar> holidaysInRange = holidayCalendarRepository
                .findByDateBetweenAndRegionCodeAndIsHolidayTrue(checkInDate, checkOutDate.minusDays(1), "CN");
        Map<LocalDate, HolidayCalendar> holidayMap = new java.util.HashMap<>();
        for (HolidayCalendar h : holidaysInRange) {
            holidayMap.put(h.getDate(), h);
        }

        LocalDate currentDate = checkInDate;
        for (int i = 0; i < nights; i++) {
            LocalDate date = currentDate.plusDays(i);

            // 从批量查询结果中获取节假日信息
            HolidayCalendar holiday = holidayMap.get(date);
            boolean isHoliday = holiday != null && Boolean.TRUE.equals(holiday.getIsHoliday());
            boolean isMakeupWorkday = holiday != null && Boolean.TRUE.equals(holiday.getIsMakeupWorkday());
            String holidayName = holiday != null ? holiday.getName() : null;
            boolean isWeekend = (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY);

            // 如果是调休补班日，按工作日处理
            if (isMakeupWorkday) {
                isWeekend = false;
            }

            // 计算当日价格：先应用日期级调价规则
            BigDecimal dailyFinalPrice = basePricePerNight;
            List<AppliedPricingRuleDTO> appliedRules = new ArrayList<>();

            for (PricingRule rule : allRules) {
                if (!isDateLevelRule(rule.getRuleType())) {
                    continue; // 跳过入住级折扣规则
                }
                if (!matchesRule(rule, homestay, date, nights, advanceDays, isWeekend, isHoliday)) {
                    continue;
                }

                BigDecimal priceBefore = dailyFinalPrice;
                dailyFinalPrice = applyAdjustment(dailyFinalPrice, rule);
                BigDecimal delta = dailyFinalPrice.subtract(priceBefore);

                appliedRules.add(AppliedPricingRuleDTO.builder()
                        .ruleId(rule.getId())
                        .ruleName(rule.getName())
                        .ruleType(rule.getRuleType())
                        .adjustmentType(rule.getAdjustmentType())
                        .adjustmentValue(rule.getAdjustmentValue())
                        .deltaAmount(delta)
                        .build());

                if (!rule.getStackable()) {
                    break; // 不可叠加，只应用第一个匹配的规则
                }
            }

            dailyPrices.add(PriceCalculationResponse.DailyPrice.builder()
                    .date(date)
                    .basePrice(basePricePerNight)
                    .price(dailyFinalPrice)
                    .finalPrice(dailyFinalPrice)
                    .isWeekend(isWeekend && !isMakeupWorkday)
                    .isHoliday(isHoliday)
                    .holidayName(holidayName)
                    .appliedRules(appliedRules)
                    .build());
        }

        return dailyPrices;
    }

    @Override
    @Transactional(readOnly = true)
    public StayDiscountResult calculateStayDiscounts(
            Homestay homestay, LocalDate checkInDate, LocalDate checkOutDate,
            int nights, int advanceDays) {

        List<PricingRule> allRules = pricingRuleRepository.findByEnabledTrueOrderByPriorityAsc();
        BigDecimal multiplier = BigDecimal.ONE;
        List<AppliedPricingRuleDTO> appliedRules = new ArrayList<>();

        // 计算订单级折扣（作用于整单房费总和的乘数）
        for (PricingRule rule : allRules) {
            if (isDateLevelRule(rule.getRuleType())) {
                continue; // 跳过日期级规则
            }
            if (!matchesRule(rule, homestay, checkInDate, nights, advanceDays, false, false)) {
                continue;
            }

            BigDecimal beforeMultiplier = multiplier;
            multiplier = applyAdjustment(multiplier, rule);

            appliedRules.add(AppliedPricingRuleDTO.builder()
                    .ruleId(rule.getId())
                    .ruleName(rule.getName())
                    .ruleType(rule.getRuleType())
                    .adjustmentType(rule.getAdjustmentType())
                    .adjustmentValue(rule.getAdjustmentValue())
                    .deltaAmount(multiplier.subtract(beforeMultiplier))
                    .build());

            if (!rule.getStackable()) {
                break;
            }
        }

        return new StayDiscountResult(multiplier, appliedRules);
    }

    /**
     * 判断是否为日期级调价规则
     */
    private boolean isDateLevelRule(String ruleType) {
        return "WEEKEND".equals(ruleType)
                || "HOLIDAY".equals(ruleType)
                || "DATE_RANGE".equals(ruleType);
    }

    /**
     * 判断规则是否匹配当前条件
     */
    private boolean matchesRule(PricingRule rule, Homestay homestay, LocalDate date,
                                 int nights, int advanceDays, boolean isWeekend, boolean isHoliday) {
        // 1. 作用域匹配
        if (!matchesScope(rule, homestay)) {
            return false;
        }

        // 2. 日期范围匹配
        if (rule.getStartDate() != null && date.isBefore(rule.getStartDate())) {
            return false;
        }
        if (rule.getEndDate() != null && date.isAfter(rule.getEndDate())) {
            return false;
        }

        // 3. 入住晚数匹配
        if (rule.getMinNights() != null && nights < rule.getMinNights()) {
            return false;
        }
        if (rule.getMaxNights() != null && nights > rule.getMaxNights()) {
            return false;
        }

        // 4. 提前天数匹配
        if (rule.getMinAdvanceDays() != null && advanceDays < rule.getMinAdvanceDays()) {
            return false;
        }
        if (rule.getMaxAdvanceDays() != null && advanceDays > rule.getMaxAdvanceDays()) {
            return false;
        }

        // 5. 规则类型特殊匹配
        String ruleType = rule.getRuleType();
        if ("WEEKEND".equals(ruleType) && !isWeekend) {
            return false;
        }
        if ("HOLIDAY".equals(ruleType) && !isHoliday) {
            return false;
        }

        return true;
    }

    /**
     * 判断规则作用域是否匹配房源。
     * 兼容两种 scopeValueJson 格式：
     * - 数组格式（推荐）：["440100"] / [123] / ["entire"]
     * - 单对象格式（旧）：{"cityCode":"440100"} / {"hostId":123} / {"type":"entire"}
     */
    private boolean matchesScope(PricingRule rule, Homestay homestay) {
        String scopeType = rule.getScopeType();
        if ("GLOBAL".equals(scopeType)) {
            return true;
        }

        if (rule.getScopeValueJson() == null || rule.getScopeValueJson().isBlank()) {
            return false;
        }

        try {
            JsonNode scopeNode = objectMapper.readTree(rule.getScopeValueJson());

            if (scopeNode.isArray()) {
                // 数组格式（与 PromotionMatchService 统一）
                return matchArrayScope(scopeType, scopeNode, homestay);
            } else {
                // 旧的单对象格式（向后兼容）
                return matchObjectScope(scopeType, scopeNode, homestay);
            }
        } catch (Exception e) {
            log.warn("解析规则作用域JSON失败, ruleId={}, json={}", rule.getId(), rule.getScopeValueJson(), e);
            return false;
        }
    }

    private boolean matchArrayScope(String scopeType, JsonNode arr, Homestay homestay) {
        return switch (scopeType) {
            case "CITY" -> {
                for (JsonNode node : arr) {
                    if (node.asText().equals(homestay.getCityCode())) yield true;
                }
                yield false;
            }
            case "HOST" -> {
                for (JsonNode node : arr) {
                    if (homestay.getOwner() != null && node.asLong() == homestay.getOwner().getId()) yield true;
                }
                yield false;
            }
            case "GROUP" -> {
                for (JsonNode node : arr) {
                    if (homestay.getGroup() != null && node.asLong() == homestay.getGroup().getId()) yield true;
                }
                yield false;
            }
            case "HOMESTAY" -> {
                for (JsonNode node : arr) {
                    if (node.asLong() == homestay.getId()) yield true;
                }
                yield false;
            }
            case "TYPE" -> {
                for (JsonNode node : arr) {
                    if (node.asText().equals(homestay.getType())) yield true;
                }
                yield false;
            }
            default -> false;
        };
    }

    private boolean matchObjectScope(String scopeType, JsonNode scopeNode, Homestay homestay) {
        return switch (scopeType) {
            case "CITY" -> {
                String cityText = scopeNode.has("cityText") ? scopeNode.get("cityText").asText() : null;
                String cityCode = scopeNode.has("cityCode") ? scopeNode.get("cityCode").asText() : null;
                yield (cityText != null && cityText.equals(homestay.getCityText()))
                        || (cityCode != null && cityCode.equals(homestay.getCityCode()));
            }
            case "HOST" -> {
                Long hostId = scopeNode.has("hostId") ? scopeNode.get("hostId").asLong() : null;
                yield hostId != null && homestay.getOwner() != null
                        && hostId.equals(homestay.getOwner().getId());
            }
            case "GROUP" -> {
                Long groupId = scopeNode.has("groupId") ? scopeNode.get("groupId").asLong() : null;
                yield groupId != null && homestay.getGroup() != null
                        && groupId.equals(homestay.getGroup().getId());
            }
            case "HOMESTAY" -> {
                Long homestayId = scopeNode.has("homestayId") ? scopeNode.get("homestayId").asLong() : null;
                yield homestayId != null && homestayId.equals(homestay.getId());
            }
            case "TYPE" -> {
                String type = scopeNode.has("type") ? scopeNode.get("type").asText() : null;
                yield type != null && type.equals(homestay.getType());
            }
            default -> false;
        };
    }

    /**
     * 应用调价规则
     */
    private BigDecimal applyAdjustment(BigDecimal base, PricingRule rule) {
        String adjustmentType = rule.getAdjustmentType();
        BigDecimal value = rule.getAdjustmentValue();

        return switch (adjustmentType) {
            case "MULTIPLY" -> base.multiply(value).setScale(2, RoundingMode.HALF_UP);
            case "DISCOUNT_RATE" -> base.multiply(BigDecimal.ONE.subtract(value)).setScale(2, RoundingMode.HALF_UP);
            case "AMOUNT_OFF" -> base.subtract(value).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            case "FIXED_PRICE" -> value.setScale(2, RoundingMode.HALF_UP);
            default -> base;
        };
    }
}
