package com.homestay3.homestaybackend.controller;

import com.homestay3.homestaybackend.annotation.RateLimit;
import com.homestay3.homestaybackend.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 支付控制器
 */
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;
    private final com.homestay3.homestaybackend.service.PaymentProcessingService paymentProcessingService;

    /**
     * 创建支付订单（生成二维码或支付页面）
     */
    @PostMapping("/{orderId}/create")
    @RateLimit(limit = 30, windowSeconds = 60)
    public ResponseEntity<Map<String, Object>> createPayment(
            @PathVariable Long orderId,
            @RequestParam String method) {
        try {
            log.info("创建支付订单，订单ID: {}, 支付方式: {}", orderId, method);

            String paymentData = paymentService.generatePaymentQRCode(orderId, method);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);

            // 根据支付方式和返回数据的格式，决定返回的字段
            if ("alipay".equals(method) && paymentData != null && paymentData.contains("<form")) {
                // 支付宝页面跳转支付，返回HTML表单
                result.put("paymentUrl", paymentData);
                result.put("message", "支付页面生成成功");
                log.info("支付宝页面支付生成成功，订单ID: {}", orderId);
            } else if (paymentData != null && paymentData.startsWith("http")) {
                // 二维码支付，返回二维码URL
                result.put("qrCode", paymentData);
                result.put("message", "支付二维码生成成功");
                log.info("支付二维码生成成功，订单ID: {}", orderId);
            } else {
                // 其他情况，通用处理
                if ("alipay".equals(method)) {
                    result.put("paymentUrl", paymentData);
                } else {
                    result.put("qrCode", paymentData);
                }
                result.put("message", "支付生成成功");
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("创建支付订单失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "创建支付失败：" + e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 查询支付状态
     */
    @GetMapping("/{orderId}/status")
    public ResponseEntity<Map<String, Object>> checkPaymentStatus(@PathVariable Long orderId) {
        try {
            log.info("查询支付状态，订单ID: {}", orderId);

            // 使用 paymentProcessingService 检查支付状态，或者维持原样如果 PaymentService 是基础支付模块
            boolean isPaid = paymentService.checkPaymentStatus(orderId);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("isPaid", isPaid);
            result.put("message", isPaid ? "支付成功" : "待支付");
            result.put("paid", isPaid); // 兼容前端可能的字段名

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("查询支付状态失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "查询失败：" + e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 模拟支付成功（仅用于测试，仅限管理员）
     */
    @PostMapping("/{orderId}/mock-success")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> mockPaymentSuccess(@PathVariable Long orderId) {
        try {
            log.info("模拟支付成功，订单ID: {}", orderId);

            paymentService.mockSuccessPayment(orderId);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "模拟支付成功");

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("模拟支付失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "模拟支付失败：" + e.getMessage());
            return ResponseEntity.ok(result);
        }
    }
}