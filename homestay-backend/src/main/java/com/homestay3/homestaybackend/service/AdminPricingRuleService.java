package com.homestay3.homestaybackend.service;

import com.homestay3.homestaybackend.entity.PricingRule;
import com.homestay3.homestaybackend.exception.ResourceNotFoundException;
import com.homestay3.homestaybackend.repository.PricingRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminPricingRuleService {

    private final PricingRuleRepository pricingRuleRepository;

    private static final Set<String> VALID_SCOPE_TYPES = Set.of(
            "GLOBAL", "HOST", "HOMESTAY", "GROUP", "CITY", "TYPE"
    );

    private static final Set<String> VALID_RULE_TYPES = Set.of(
            "WEEKEND", "HOLIDAY", "DATE_RANGE", "EARLY_BIRD", "LONG_STAY"
    );

    private static final Set<String> VALID_ADJUSTMENT_TYPES = Set.of(
            "MULTIPLY", "DISCOUNT_RATE", "AMOUNT_OFF", "FIXED_PRICE"
    );

    @Transactional(readOnly = true)
    public Page<PricingRule> listRules(int page, int size, String ruleType) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("priority").ascending());
        if (StringUtils.hasText(ruleType)) {
            List<PricingRule> list = pricingRuleRepository.findByRuleTypeAndEnabledTrueOrderByPriorityAsc(ruleType);
            int start = (int) pageable.getOffset();
            int end = Math.min(start + size, list.size());
            List<PricingRule> pageContent = start < list.size() ? list.subList(start, end) : List.of();
            return new PageImpl<>(pageContent, pageable, list.size());
        }
        return pricingRuleRepository.findAll(pageable);
    }

    @Transactional
    @CacheEvict(value = "pricingRules", allEntries = true)
    public PricingRule createRule(PricingRule rule, Long userId) {
        validateRule(rule);
        rule.setId(null);
        rule.setCreatedBy(userId);
        return pricingRuleRepository.save(rule);
    }

    @Transactional
    @CacheEvict(value = "pricingRules", allEntries = true)
    public PricingRule updateRule(Long id, PricingRule rule, Long userId) {
        PricingRule existing = pricingRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PricingRule", "id", id));
        if (!userId.equals(existing.getCreatedBy())) {
            throw new SecurityException("无权修改该定价规则");
        }
        validateRule(rule);
        existing.setName(rule.getName());
        existing.setScopeType(rule.getScopeType());
        existing.setScopeValueJson(rule.getScopeValueJson());
        existing.setRuleType(rule.getRuleType());
        existing.setAdjustmentType(rule.getAdjustmentType());
        existing.setAdjustmentValue(rule.getAdjustmentValue());
        existing.setPriority(rule.getPriority());
        existing.setStackable(rule.getStackable());
        existing.setStartDate(rule.getStartDate());
        existing.setEndDate(rule.getEndDate());
        existing.setMinNights(rule.getMinNights());
        existing.setMaxNights(rule.getMaxNights());
        existing.setMinAdvanceDays(rule.getMinAdvanceDays());
        existing.setMaxAdvanceDays(rule.getMaxAdvanceDays());
        existing.setEnabled(rule.getEnabled());
        return pricingRuleRepository.save(existing);
    }

    @Transactional
    @CacheEvict(value = "pricingRules", allEntries = true)
    public void deleteRule(Long id, Long userId) {
        PricingRule existing = pricingRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PricingRule", "id", id));
        if (!userId.equals(existing.getCreatedBy())) {
            throw new SecurityException("无权删除该定价规则");
        }
        pricingRuleRepository.delete(existing);
    }

    @Transactional
    @CacheEvict(value = "pricingRules", allEntries = true)
    public PricingRule toggleRule(Long id, Long userId) {
        PricingRule rule = pricingRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PricingRule", "id", id));
        if (!userId.equals(rule.getCreatedBy())) {
            throw new SecurityException("无权切换该定价规则状态");
        }
        rule.setEnabled(!rule.getEnabled());
        return pricingRuleRepository.save(rule);
    }

    private void validateRule(PricingRule rule) {
        if (!StringUtils.hasText(rule.getName())) {
            throw new IllegalArgumentException("规则名称不能为空");
        }
        if (rule.getName().length() > 200) {
            throw new IllegalArgumentException("规则名称不能超过200字符");
        }

        if (!StringUtils.hasText(rule.getScopeType())) {
            throw new IllegalArgumentException("作用域类型不能为空");
        }
        if (!VALID_SCOPE_TYPES.contains(rule.getScopeType())) {
            throw new IllegalArgumentException("非法的作用域类型: " + rule.getScopeType());
        }

        if (!"GLOBAL".equals(rule.getScopeType())) {
            if (!StringUtils.hasText(rule.getScopeValueJson())) {
                throw new IllegalArgumentException("非全局规则必须设置作用域值");
            }
        }

        if (!StringUtils.hasText(rule.getRuleType())) {
            throw new IllegalArgumentException("规则类型不能为空");
        }
        if (!VALID_RULE_TYPES.contains(rule.getRuleType())) {
            throw new IllegalArgumentException("非法的规则类型: " + rule.getRuleType());
        }

        if (!StringUtils.hasText(rule.getAdjustmentType())) {
            throw new IllegalArgumentException("调价类型不能为空");
        }
        if (!VALID_ADJUSTMENT_TYPES.contains(rule.getAdjustmentType())) {
            throw new IllegalArgumentException("非法的调价类型: " + rule.getAdjustmentType());
        }

        if (rule.getAdjustmentValue() == null) {
            throw new IllegalArgumentException("调价数值不能为空");
        }
        if (rule.getAdjustmentValue().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("调价数值不能为负数");
        }

        if (rule.getPriority() == null) {
            throw new IllegalArgumentException("优先级不能为空");
        }

        if (rule.getStartDate() != null && rule.getEndDate() != null) {
            if (rule.getStartDate().isAfter(rule.getEndDate())) {
                throw new IllegalArgumentException("开始日期不能晚于结束日期");
            }
        }

        if (rule.getMinNights() != null && rule.getMaxNights() != null) {
            if (rule.getMinNights() > rule.getMaxNights()) {
                throw new IllegalArgumentException("最少入住晚数不能大于最多入住晚数");
            }
        }

        if (rule.getMinAdvanceDays() != null && rule.getMaxAdvanceDays() != null) {
            if (rule.getMinAdvanceDays() > rule.getMaxAdvanceDays()) {
                throw new IllegalArgumentException("最少提前天数不能大于最多提前天数");
            }
        }
    }
}
