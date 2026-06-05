package com.homestay3.homestaybackend.service;

import com.homestay3.homestaybackend.entity.PricingRule;
import com.homestay3.homestaybackend.exception.ResourceNotFoundException;
import com.homestay3.homestaybackend.repository.PricingRuleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminPricingRuleServiceTest {

    @Mock
    private PricingRuleRepository pricingRuleRepository;

    @InjectMocks
    private AdminPricingRuleService adminPricingRuleService;

    private static final Long ADMIN_A = 100L;
    private static final Long ADMIN_B = 200L;

    private void stubSaveToReturnSame() {
        when(pricingRuleRepository.save(any(PricingRule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private PricingRule validRule() {
        return PricingRule.builder()
                .name("周末加价")
                .scopeType("GLOBAL")
                .ruleType("WEEKEND")
                .adjustmentType("MULTIPLY")
                .adjustmentValue(new BigDecimal("1.20"))
                .priority(10)
                .stackable(true)
                .enabled(true)
                .build();
    }

    @Test
    @DisplayName("createRule 合法入参时把 createdBy 设为当前管理员")
    void createRuleSetsCreatedBy() {
        stubSaveToReturnSame();
        PricingRule input = validRule();
        input.setId(999L);

        PricingRule saved = adminPricingRuleService.createRule(input, ADMIN_A);

        ArgumentCaptor<PricingRule> captor = ArgumentCaptor.forClass(PricingRule.class);
        verify(pricingRuleRepository).save(captor.capture());
        PricingRule persisted = captor.getValue();

        assertThat(persisted.getId()).isNull();
        assertThat(persisted.getCreatedBy()).isEqualTo(ADMIN_A);
        assertThat(saved.getCreatedBy()).isEqualTo(ADMIN_A);
    }

    @Test
    @DisplayName("createRule 名称为空时抛出 IllegalArgumentException，不写库")
    void createRuleRejectsBlankName() {
        PricingRule rule = validRule();
        rule.setName("");

        assertThatThrownBy(() -> adminPricingRuleService.createRule(rule, ADMIN_A))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("规则名称");

        verify(pricingRuleRepository, never()).save(any());
    }

    @Test
    @DisplayName("createRule 非法 adjustmentType 时拒绝")
    void createRuleRejectsInvalidAdjustmentType() {
        PricingRule rule = validRule();
        rule.setAdjustmentType("NOPE");

        assertThatThrownBy(() -> adminPricingRuleService.createRule(rule, ADMIN_A))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("调价类型");

        verify(pricingRuleRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteRule 创建者可成功删除")
    void deleteRuleAllowsOwner() {
        PricingRule existing = validRule();
        existing.setId(1L);
        existing.setCreatedBy(ADMIN_A);
        when(pricingRuleRepository.findById(1L)).thenReturn(Optional.of(existing));

        adminPricingRuleService.deleteRule(1L, ADMIN_A);

        verify(pricingRuleRepository).delete(existing);
    }

    @Test
    @DisplayName("deleteRule 非创建者删除应抛出 SecurityException，不删数据")
    void deleteRuleRejectsNonOwner() {
        PricingRule existing = validRule();
        existing.setId(1L);
        existing.setCreatedBy(ADMIN_A);
        when(pricingRuleRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> adminPricingRuleService.deleteRule(1L, ADMIN_B))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("无权");

        verify(pricingRuleRepository, never()).delete(any(PricingRule.class));
    }

    @Test
    @DisplayName("deleteRule 规则不存在时抛 ResourceNotFoundException")
    void deleteRuleThrowsWhenMissing() {
        when(pricingRuleRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminPricingRuleService.deleteRule(404L, ADMIN_A))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(pricingRuleRepository, never()).delete(any(PricingRule.class));
    }

    @Test
    @DisplayName("updateRule 非创建者不能修改")
    void updateRuleRejectsNonOwner() {
        PricingRule existing = validRule();
        existing.setId(1L);
        existing.setCreatedBy(ADMIN_A);
        when(pricingRuleRepository.findById(1L)).thenReturn(Optional.of(existing));

        PricingRule patch = validRule();
        patch.setName("改个名");

        assertThatThrownBy(() -> adminPricingRuleService.updateRule(1L, patch, ADMIN_B))
                .isInstanceOf(SecurityException.class);

        verify(pricingRuleRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateRule 创建者正常修改")
    void updateRuleAllowsOwner() {
        stubSaveToReturnSame();
        PricingRule existing = validRule();
        existing.setId(1L);
        existing.setCreatedBy(ADMIN_A);
        when(pricingRuleRepository.findById(1L)).thenReturn(Optional.of(existing));

        PricingRule patch = validRule();
        patch.setName("新名字");
        patch.setAdjustmentValue(new BigDecimal("1.50"));

        PricingRule updated = adminPricingRuleService.updateRule(1L, patch, ADMIN_A);

        assertThat(updated.getName()).isEqualTo("新名字");
        assertThat(updated.getAdjustmentValue()).isEqualByComparingTo("1.50");
        assertThat(updated.getCreatedBy()).isEqualTo(ADMIN_A);
    }

    @Test
    @DisplayName("toggleRule 非创建者不能切换状态")
    void toggleRuleRejectsNonOwner() {
        PricingRule existing = validRule();
        existing.setId(1L);
        existing.setEnabled(true);
        existing.setCreatedBy(ADMIN_A);
        when(pricingRuleRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> adminPricingRuleService.toggleRule(1L, ADMIN_B))
                .isInstanceOf(SecurityException.class);

        verify(pricingRuleRepository, never()).save(any());
    }

    @Test
    @DisplayName("toggleRule 创建者可以翻转 enabled 字段")
    void toggleRuleAllowsOwner() {
        stubSaveToReturnSame();
        PricingRule existing = validRule();
        existing.setId(1L);
        existing.setEnabled(true);
        existing.setCreatedBy(ADMIN_A);
        when(pricingRuleRepository.findById(1L)).thenReturn(Optional.of(existing));

        PricingRule toggled = adminPricingRuleService.toggleRule(1L, ADMIN_A);

        assertThat(toggled.getEnabled()).isFalse();
    }
}
