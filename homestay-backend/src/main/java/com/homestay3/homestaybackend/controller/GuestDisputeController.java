package com.homestay3.homestaybackend.controller;

import com.homestay3.homestaybackend.dto.OrderDTO;
import com.homestay3.homestaybackend.entity.User;
import com.homestay3.homestaybackend.exception.AccessDeniedException;
import com.homestay3.homestaybackend.exception.ResourceNotFoundException;
import com.homestay3.homestaybackend.repository.UserRepository;
import com.homestay3.homestaybackend.service.DisputeService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 房客端争议控制器
 * 房客对"退款被拒/押金扣押/入住体验严重不符"发起争议
 */
@RestController
@RequestMapping("/api/guest/disputes")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
public class GuestDisputeController {

    private final DisputeService disputeService;
    private final UserRepository userRepository;
    private static final Logger log = LoggerFactory.getLogger(GuestDisputeController.class);

    /**
     * 房客发起争议
     * 请求体: { "orderId": 123, "reason": "..." }
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> raiseDisputeByGuest(@RequestBody Map<String, Object> requestBody) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "message", "用户未登录"
            ));
        }

        try {
            Object orderIdObj = requestBody.get("orderId");
            if (orderIdObj == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "订单ID不能为空"
                ));
            }
            Long orderId = Long.valueOf(orderIdObj.toString());
            String reason = requestBody.getOrDefault("reason", "").toString();
            if (reason.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "请输入争议原因"
                ));
            }

            OrderDTO updatedOrder = disputeService.raiseDisputeByGuest(orderId, reason);
            log.info("房客发起争议成功，订单ID: {}", orderId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", updatedOrder
            ));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (AccessDeniedException e) {
            log.warn("房客发起争议权限检查失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("房客发起争议失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "发起争议失败: " + e.getMessage()
            ));
        }
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        String username = authentication.getName();
        return userRepository.findByUsername(username).orElse(null);
    }
}
