package com.homestay3.homestaybackend.controller;

import com.homestay3.homestaybackend.dto.DisputeAdvisorResult;
import com.homestay3.homestaybackend.exception.ResourceNotFoundException;
import com.homestay3.homestaybackend.service.DisputeAdvisorService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理员争议辅助控制器
 * 仲裁争议订单前一键生成"裁决建议草稿"（仅建议，最终审批权在管理员）
 */
@RestController
@RequestMapping("/api/admin/disputes/advisor")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDisputeAdvisorController {

    private static final Logger log = LoggerFactory.getLogger(AdminDisputeAdvisorController.class);

    private final DisputeAdvisorService disputeAdvisorService;

    /**
     * 生成争议订单的裁决建议草稿
     */
    @GetMapping("/{orderId}/advice")
    public ResponseEntity<Map<String, Object>> generateAdvice(@PathVariable Long orderId) {
        log.info("管理员生成裁决建议草稿，订单ID: {}", orderId);
        try {
            DisputeAdvisorResult result = disputeAdvisorService.generateAdvice(orderId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", result
            ));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(400).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("生成裁决建议草稿失败，订单ID: {}", orderId, e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "生成裁决建议失败: " + e.getMessage()
            ));
        }
    }
}
