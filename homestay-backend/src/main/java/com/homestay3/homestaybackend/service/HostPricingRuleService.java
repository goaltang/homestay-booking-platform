package com.homestay3.homestaybackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homestay3.homestaybackend.entity.PricingRule;
import com.homestay3.homestaybackend.repository.PricingRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class HostPricingRuleService {

    private final PricingRuleRepository pricingRuleRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<PricingRule> listRules(Long userId) {
        List<PricingRule> rules = pricingRuleRepository.findByEnabledTrueOrderByPriorityAsc();
        return rules.stream()
                .filter(r -> userId.equals(r.getCreatedBy())
                        || ("HOST".equals(r.getScopeType()) && matchesHostScope(r.getScopeValueJson(), userId)))
                .toList();
    }

    @Transactional
    @CacheEvict(value = "pricingRules", allEntries = true)
    public PricingRule createRule(PricingRule rule, Long userId) {
        rule.setCreatedBy(userId);
        if (!List.of("HOST", "HOMESTAY", "GROUP").contains(rule.getScopeType())) {
            throw new IllegalArgumentException("房东只能创建 HOST、HOMESTAY 或 GROUP 作用域的规则");
        }
        return pricingRuleRepository.save(rule);
    }

    @Transactional
    @CacheEvict(value = "pricingRules", allEntries = true)
    public PricingRule updateRule(Long id, PricingRule rule, Long userId) {
        PricingRule existing = pricingRuleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("规则不存在"));
        if (!userId.equals(existing.getCreatedBy())) {
            throw new SecurityException("无权修改此规则");
        }
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
        PricingRule rule = pricingRuleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("规则不存在"));
        if (!userId.equals(rule.getCreatedBy())) {
            throw new SecurityException("无权删除此规则");
        }
        pricingRuleRepository.deleteById(id);
    }

    /**
     * 解析 scopeValueJson 匹配房东 ID，兼容数组和对象两种格式。
     */
    private boolean matchesHostScope(String scopeValueJson, Long userId) {
        if (scopeValueJson == null || scopeValueJson.isBlank()) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(scopeValueJson);
            if (node.isArray()) {
                for (JsonNode item : node) {
                    if (item.isNumber() && item.asLong() == userId) return true;
                    if (item.isObject() && item.has("hostId") && item.get("hostId").asLong() == userId) return true;
                }
                return false;
            }
            if (node.has("hostId") && node.get("hostId").asLong() == userId) {
                return true;
            }
        } catch (Exception e) {
            log.warn("解析 scopeValueJson 失败: {}", scopeValueJson, e);
        }
        return false;
    }
}
