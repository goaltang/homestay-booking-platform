package com.homestay3.homestaybackend.controller;

import com.homestay3.homestaybackend.dto.ApiResponse;
import com.homestay3.homestaybackend.entity.PricingRule;
import com.homestay3.homestaybackend.service.AdminPricingRuleService;
import com.homestay3.homestaybackend.util.UserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/pricing-rules")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPricingRuleController {

    private final AdminPricingRuleService adminPricingRuleService;

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String ruleType) {
        Page<PricingRule> result = adminPricingRuleService.listRules(page, size, ruleType);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody PricingRule rule) {
        Long userId = UserUtil.getCurrentUserId();
        PricingRule saved = adminPricingRuleService.createRule(rule, userId);
        return ResponseEntity.ok(ApiResponse.success(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody PricingRule rule) {
        Long userId = UserUtil.getCurrentUserId();
        try {
            PricingRule saved = adminPricingRuleService.updateRule(id, rule, userId);
            return ResponseEntity.ok(ApiResponse.success(saved));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(403, e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        Long userId = UserUtil.getCurrentUserId();
        try {
            adminPricingRuleService.deleteRule(id, userId);
            return ResponseEntity.ok(ApiResponse.success(null));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(403, e.getMessage()));
        }
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<?> toggle(@PathVariable Long id) {
        Long userId = UserUtil.getCurrentUserId();
        try {
            PricingRule rule = adminPricingRuleService.toggleRule(id, userId);
            return ResponseEntity.ok(ApiResponse.success(rule));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(403, e.getMessage()));
        }
    }
}
