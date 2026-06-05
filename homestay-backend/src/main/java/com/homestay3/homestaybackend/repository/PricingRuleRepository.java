package com.homestay3.homestaybackend.repository;

import com.homestay3.homestaybackend.entity.PricingRule;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PricingRuleRepository extends JpaRepository<PricingRule, Long>, JpaSpecificationExecutor<PricingRule> {

    @Cacheable(value = "pricingRules", key = "'enabled-sorted'")
    List<PricingRule> findByEnabledTrueOrderByPriorityAsc();

    List<PricingRule> findByRuleTypeAndEnabledTrueOrderByPriorityAsc(String ruleType);
}
