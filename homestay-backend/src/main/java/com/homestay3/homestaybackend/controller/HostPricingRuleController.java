package com.homestay3.homestaybackend.controller;

import com.homestay3.homestaybackend.entity.PricingRule;
import com.homestay3.homestaybackend.security.CustomUserDetails;
import com.homestay3.homestaybackend.service.HostPricingRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/host/pricing-rules")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@PreAuthorize("hasRole('HOST') or hasRole('LANDLORD')")
public class HostPricingRuleController {

    private final HostPricingRuleService hostPricingRuleService;

    @GetMapping
    public ResponseEntity<?> list() {
        CustomUserDetails user = getCurrentUser();
        List<PricingRule> hostRules = hostPricingRuleService.listRules(user.getUserId());
        return ResponseEntity.ok(hostRules);
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody PricingRule rule) {
        CustomUserDetails user = getCurrentUser();
        try {
            PricingRule saved = hostPricingRuleService.createRule(rule, user.getUserId());
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody PricingRule rule) {
        CustomUserDetails user = getCurrentUser();
        try {
            PricingRule updated = hostPricingRuleService.updateRule(id, rule, user.getUserId());
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        CustomUserDetails user = getCurrentUser();
        try {
            hostPricingRuleService.deleteRule(id, user.getUserId());
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        }
    }

    private CustomUserDetails getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (CustomUserDetails) auth.getPrincipal();
    }
}
