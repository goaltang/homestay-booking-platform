package com.homestay3.homestaybackend.controller;

import com.homestay3.homestaybackend.dto.AuthResponse;
import com.homestay3.homestaybackend.dto.PasswordChangeRequest;
import com.homestay3.homestaybackend.dto.ProfileUpdateRequest;
import com.homestay3.homestaybackend.repository.NotificationRepository;
import com.homestay3.homestaybackend.repository.OrderRepository;
import com.homestay3.homestaybackend.repository.UserCouponRepository;
import com.homestay3.homestaybackend.repository.UserFavoriteRepository;
import com.homestay3.homestaybackend.security.DemoAccountGuard;
import com.homestay3.homestaybackend.service.FileService;
import com.homestay3.homestaybackend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final FileService fileService;
    private final OrderRepository orderRepository;
    private final UserFavoriteRepository userFavoriteRepository;
    private final UserCouponRepository userCouponRepository;
    private final NotificationRepository notificationRepository;
    private final DemoAccountGuard demoAccountGuard;

    @PutMapping("/profile")
    public ResponseEntity<AuthResponse> updateProfile(
            @Valid @RequestBody ProfileUpdateRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        demoAccountGuard.assertModifiable(userDetails.getUsername());
        return ResponseEntity.ok(userService.updateProfile(request, userDetails.getUsername()));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody PasswordChangeRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        demoAccountGuard.assertModifiable(userDetails.getUsername());
        userService.changePassword(request, userDetails.getUsername());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        var user = userService.getUserByUsername(userDetails.getUsername());
        Long userId = user.getId();

        Long pendingPayment = orderRepository.countByGuestIdAndStatus(userId, "PAYMENT_PENDING");
        if (pendingPayment == null) pendingPayment = 0L;
        Long confirmed = orderRepository.countByGuestIdAndStatus(userId, "CONFIRMED");
        if (confirmed == null) confirmed = 0L;

        Long pendingReview = orderRepository.countPendingReviewOrders(userId);
        if (pendingReview == null) pendingReview = 0L;

        Long favoriteCount = userFavoriteRepository.countByUserId(userId);
        if (favoriteCount == null) favoriteCount = 0L;

        Long couponCount = userCouponRepository.countByUserIdAndStatusAndExpireAtAfter(
                userId, "AVAILABLE", LocalDateTime.now());
        if (couponCount == null) couponCount = 0L;

        Long unreadCount = notificationRepository.countByUserIdAndIsReadFalse(userId);
        if (unreadCount == null) unreadCount = 0L;

        Map<String, Object> result = new HashMap<>();
        result.put("pendingPaymentCount", pendingPayment + confirmed);
        result.put("pendingReviewCount", pendingReview);
        result.put("favoriteCount", favoriteCount);
        result.put("couponCount", couponCount);
        result.put("unreadNotificationCount", unreadCount);
        return ResponseEntity.ok(result);
    }
} 