package com.homestay3.homestaybackend.controller;

import com.homestay3.homestaybackend.dto.AdminLoginRequest;
import com.homestay3.homestaybackend.dto.AdminLoginResponse;
import com.homestay3.homestaybackend.dto.AdminIdentityResponse;
import com.homestay3.homestaybackend.security.AuthTokenResolver;
import com.homestay3.homestaybackend.security.CustomUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import com.homestay3.homestaybackend.service.AuthenticationService;
import com.homestay3.homestaybackend.service.LoginLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AuthenticationService authenticationService;
    private final LoginLogService loginLogService;

    @PostMapping("/login")
    public ResponseEntity<AdminLoginResponse> login(@RequestBody AdminLoginRequest request,
                                                    HttpServletRequest httpRequest) {
        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        try {
            AdminLoginResponse response = authenticationService.login(request);
            // 记录登录成功日志
            loginLogService.recordLogin(request.getUsername(), ipAddress, userAgent, "SUCCESS", "ADMIN");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            // 记录登录失败日志
            loginLogService.recordLogin(request.getUsername(), ipAddress, userAgent, "FAIL", "ADMIN");
            throw e;
        }
    }

    @PostMapping("/logout")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        authenticationService.logout(AuthTokenResolver.resolve(httpRequest));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/info")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<AdminIdentityResponse> getAdminInfo(Authentication authentication) {
        CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
        return ResponseEntity.ok(new AdminIdentityResponse(
                principal.getUserId(), principal.getUsername(), "ROLE_ADMIN"));
    }

    /**
     * 获取客户端真实IP地址
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 如果是多个IP，取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
} 