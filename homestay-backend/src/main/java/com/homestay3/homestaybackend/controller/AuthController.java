package com.homestay3.homestaybackend.controller;

import com.homestay3.homestaybackend.dto.AuthRequest;
import com.homestay3.homestaybackend.dto.AuthResponse;
import com.homestay3.homestaybackend.dto.PasswordResetRequest;
import com.homestay3.homestaybackend.dto.RegisterRequest;
import com.homestay3.homestaybackend.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@Validated
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "用户认证", description = "C端用户注册、登录、密码找回等认证接口")
public class AuthController {

    private final AuthService authService;
    private final com.homestay3.homestaybackend.security.JwtTokenProvider jwtTokenProvider;
    private final com.homestay3.homestaybackend.security.AuthCookieService authCookieService;
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "注册新用户并返回 JWT")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 HttpServletResponse servletResponse) {
        log.info("注册请求: {}", request.getUsername());
        try {
            AuthResponse response = authService.register(request);
            if (response != null && response.getToken() != null) {
                setAuthCookie(servletResponse, response.getToken());
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("注册失败: {}", e.getMessage());
            throw e;
        }
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "用户名密码登录，返回 JWT")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request,
                                              HttpServletResponse servletResponse) {
        log.info("登录请求: {}", request.getUsername());
        try {
            AuthResponse response = authService.login(request);
            if (response != null && response.getToken() != null) {
                setAuthCookie(servletResponse, response.getToken());
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("登录失败: {}", e.getMessage());
            throw e;
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@RequestParam @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不正确") String email) {
        log.info("忘记密码请求: {}", email);
        try {
            authService.forgotPassword(email);
            Map<String, String> response = new HashMap<>();
            response.put("message", "如果邮箱已注册，密码重置邮件将发送至该邮箱");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("忘记密码处理失败: {}", e.getMessage());
            throw e;
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        log.info("重置密码请求");
        try {
            authService.resetPassword(request);
            Map<String, String> response = new HashMap<>();
            response.put("message", "密码重置成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("重置密码失败: {}", e.getMessage());
            throw e;
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "用户登出", description = "清除 httpOnly 认证 Cookie")
    public ResponseEntity<Map<String, String>> logout(HttpServletResponse servletResponse,
                                                     jakarta.servlet.http.HttpServletRequest request) {
        jwtTokenProvider.revokeToken(com.homestay3.homestaybackend.security.AuthTokenResolver.cookie(request));
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        clearAuthCookie(servletResponse);
        Map<String, String> response = new HashMap<>();
        response.put("message", "已退出登录");
        return ResponseEntity.ok(response);
    }

    /**
     * 头像上传功能已迁移到FileController统一处理
     * 请使用 /api/files/upload?type=avatar 端点
     */

    @GetMapping("/user-info")
    public ResponseEntity<AuthResponse> getUserInfo(@RequestParam String username) {
        log.info("获取用户信息请求: {}", username);
        try {
            AuthResponse response = authService.getUserInfo(username);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取用户信息失败: {}", e.getMessage());
            throw e;
        }
    }
    
    /**
     * 检查用户名是否已存在
     * @param username 用户名
     * @return 包含exists字段的JSON，表示用户名是否存在
     */
    @GetMapping("/check-username")
    public ResponseEntity<Map<String, Boolean>> checkUsername(@RequestParam String username) {
        log.info("检查用户名是否存在: {}", username);
        boolean exists = authService.isUsernameExists(username);
        log.info("用户名 {} 存在状态: {}", username, exists);
        return ResponseEntity.ok(Map.of("exists", exists));
    }
    
    /**
     * 检查邮箱是否已存在
     * @param email 邮箱
     * @return 包含exists字段的JSON，表示邮箱是否存在
     */
    @GetMapping("/check-email")
    public ResponseEntity<Map<String, Boolean>> checkEmail(@RequestParam String email) {
        log.info("检查邮箱是否存在: {}", email);
        boolean exists = authService.isEmailExists(email);
        log.info("邮箱 {} 存在状态: {}", email, exists);
        return ResponseEntity.ok(Map.of("exists", exists));
    }

    /**
     * 获取当前认证用户信息
     * @param authentication Spring Security认证对象
     * @return 当前用户信息
     */
    @GetMapping("/current")
    public ResponseEntity<AuthResponse> getCurrentUser(Authentication authentication) {
        if (authentication == null) {
            log.warn("获取当前用户信息失败: 未认证");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        log.info("获取当前用户信息: {}", authentication.getName());
        try {
            // 获取基本用户信息
            AuthResponse response = authService.getUserInfo(authentication.getName());
            
            // 确保角色信息正确返回
            if (response != null) {
                log.info("用户 {} 的角色: {}", authentication.getName(), response.getRole());
                
                // 添加authorities信息到响应
                if (authentication.getAuthorities() != null && !authentication.getAuthorities().isEmpty()) {
                    List<String> authorities = authentication.getAuthorities().stream()
                        .map(auth -> auth.getAuthority())
                        .collect(Collectors.toList());
                    response.setAuthorities(authorities);
                    log.info("用户 {} 的authorities: {}", authentication.getName(), authorities);
                }
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取当前用户信息失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(AuthResponse.builder()
                            .username(null)
                            .id(null) 
                            .email(null)
                            .phone(null)
                            .realName(null)
                            .avatar(null)
                            .build());
        }
    }
    



    private void setAuthCookie(HttpServletResponse response, String token) {
        authCookieService.set(response, token);
    }

    private void clearAuthCookie(HttpServletResponse response) {
        authCookieService.clear(response);
    }

} 