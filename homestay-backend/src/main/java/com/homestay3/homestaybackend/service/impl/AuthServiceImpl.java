package com.homestay3.homestaybackend.service.impl;

import com.homestay3.homestaybackend.dto.*;
import com.homestay3.homestaybackend.entity.User;
import com.homestay3.homestaybackend.exception.LoginException;
import com.homestay3.homestaybackend.exception.CredentialValidationException;
import com.homestay3.homestaybackend.mapper.UserMapper;
import com.homestay3.homestaybackend.model.VerificationStatus;
import com.homestay3.homestaybackend.model.UserRole;
import com.homestay3.homestaybackend.repository.UserRepository;
import com.homestay3.homestaybackend.repository.CouponTemplateRepository;
import com.homestay3.homestaybackend.service.CouponService;
import com.homestay3.homestaybackend.security.JwtTokenProvider;
import com.homestay3.homestaybackend.security.AccountRole;
import com.homestay3.homestaybackend.service.AuthService;
import com.homestay3.homestaybackend.service.EmailService;
import com.homestay3.homestaybackend.service.NotificationService;
import com.homestay3.homestaybackend.model.enums.NotificationType;
import com.homestay3.homestaybackend.model.enums.EntityType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final UserMapper userMapper;
    private final CouponTemplateRepository couponTemplateRepository;
    private final CouponService couponService;
    private final com.homestay3.homestaybackend.service.ReferralService referralService;
    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);
    private static final SecureRandom RESET_TOKEN_RANDOM = new SecureRandom();

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String role = publicRegistrationRole(request.getRole());
        log.info("开始注册用户: {}", request.getUsername());
        
        try {
            // 先查询用户数量
            log.info("当前用户总数: {}", userRepository.count());
            
            // 检查用户名是否存在
            boolean usernameExists = userRepository.existsByUsername(request.getUsername());
            log.info("用户名 {} 是否存在: {}", request.getUsername(), usernameExists);
            if (usernameExists) {
                log.warn("用户名已存在: {}", request.getUsername());
                throw new CredentialValidationException("用户名已存在");
            }

            // 检查邮箱是否存在
            boolean emailExists = userRepository.existsByEmail(request.getEmail());
            log.info("邮箱 {} 是否存在: {}", request.getEmail(), emailExists);
            if (emailExists) {
                log.warn("邮箱已存在: {}", request.getEmail());
                throw new CredentialValidationException("邮箱已存在");
            }

            // 创建新用户
            User user = new User();
            user.setUsername(request.getUsername());
            user.setEmail(request.getEmail());
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            user.setPhone(request.getPhone());
            
            user.setRole(role);
            user.setEnabled(true);
            user.setVerificationStatus(VerificationStatus.UNVERIFIED);
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            
            // 保存用户并立即刷新
            User savedUser = userRepository.saveAndFlush(user);
            log.info("用户创建成功，ID: {}, 用户名: {}", savedUser.getId(), savedUser.getUsername());

            // 发送欢迎通知
            try {
                notificationService.createNotification(
                        savedUser.getId(),       // 接收者: 新用户
                        null,                   // 触发者: 系统 (null)
                        NotificationType.WELCOME_MESSAGE, // 类型: 欢迎消息
                        EntityType.USER,        // 关联实体类型: 用户
                        String.valueOf(savedUser.getId()), // 关联实体ID: 新用户ID
                        "欢迎加入民宿预订平台！开始探索精彩的住宿吧。" // 内容
                );
                log.info("已为新用户 {} 发送欢迎通知", savedUser.getUsername());
            } catch (Exception e) {
                log.error("为新用户 {} 发送欢迎通知失败: {}", savedUser.getUsername(), e.getMessage(), e);
                // 发送通知失败不应中断注册流程
            }

            // 自动发放新人专属优惠券
            try {
                LocalDateTime now = LocalDateTime.now();
                var newUserTemplates = couponTemplateRepository.findActiveNewUserTemplates("ACTIVE", now);
                if (!newUserTemplates.isEmpty()) {
                    for (var template : newUserTemplates) {
                        try {
                            couponService.claimCoupon(savedUser.getId(), template.getId());
                            log.info("已向新用户 {} 自动发放新人券 templateId={}", savedUser.getUsername(), template.getId());
                        } catch (Exception ce) {
                            log.warn("向新用户 {} 发放新人券 templateId={} 失败: {}",
                                    savedUser.getUsername(), template.getId(), ce.getMessage());
                        }
                    }
                } else {
                    log.info("当前无激活的新人券模板，跳过自动发放");
                }
            } catch (Exception e) {
                log.error("新人券自动发放流程异常: {}", e.getMessage(), e);
                // 新人券发放失败不应中断注册流程
            }

            // 处理邀请码（分享裂变）
            try {
                String referralCode = request.getReferralCode();
                if (referralCode != null && !referralCode.isBlank()) {
                    boolean success = referralService.useReferralCode(referralCode, savedUser.getId());
                    if (success) {
                        log.info("新用户 {} 使用邀请码 {} 成功，奖励已发放", savedUser.getUsername(), referralCode);
                    } else {
                        log.warn("新用户 {} 使用邀请码 {} 失败，可能已过期或无效", savedUser.getUsername(), referralCode);
                    }
                }
            } catch (Exception e) {
                log.warn("邀请码处理失败: {}", e.getMessage());
                // 邀请码处理失败不应中断注册流程
            }

            // 生成token - 修改这部分，避免使用userDetailsService
            String token = generateTokenForNewUser(savedUser);

            // 返回认证响应
            AuthResponse response = new AuthResponse();
            response.setToken(token);
            response.setUser(convertToDTO(savedUser));
            
            log.info("注册完成，返回响应");
            return response;
        } catch (Exception e) {
            log.error("注册失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public AuthResponse login(AuthRequest request) {
        log.info("用户登录: {}", request.getUsername());

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new LoginException("用户不存在，请检查用户名或先注册账号"));

        String role;
        try {
            role = AccountRole.normalize(user.getRole());
        } catch (IllegalArgumentException e) {
            throw new LoginException("账号角色无效，请联系管理员");
        }
        // 拒绝管理员从前端登录
        if (UserRole.ROLE_ADMIN.name().equals(role)) {
            log.warn("管理员尝试从前端登录: {}", request.getUsername());
            throw new LoginException("管理员请使用管理后台登录");
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.info("用户信息: id={}, username={}, realName={}",
                user.getId(), user.getUsername(), user.getRealName());

            // 仅更新登录时间，不能用旧实体快照覆盖并发提交的新密码和重置令牌。
            userRepository.updateLastLogin(user.getId(), LocalDateTime.now());

            String token = jwtTokenProvider.generateToken(authentication);

            AuthResponse response = new AuthResponse();
            response.setToken(token);
            response.setUser(convertToDTO(user));
            log.info("用户登录成功: userId={}", user.getId());
            return response;

        } catch (org.springframework.security.authentication.BadCredentialsException e) {
            log.warn("密码错误: {}", request.getUsername());
            throw new LoginException("密码错误，请重新输入");
        } catch (org.springframework.security.authentication.DisabledException e) {
            log.warn("账号已被禁用: {}", request.getUsername());
            throw new LoginException("账号已被禁用，请联系管理员");
        } catch (org.springframework.security.authentication.LockedException e) {
            log.warn("账号已被锁定: {}", request.getUsername());
            throw new LoginException("账号已被锁定，请联系管理员");
        } catch (LoginException e) {
            throw e;
        } catch (Exception e) {
            log.error("登录过程中发生未知错误: {}", e.getMessage());
            throw new RuntimeException("登录失败，请稍后重试");
        }
    }

    @Override
    @Transactional
    public void forgotPassword(String email) {
        User user = userRepository.findByEmailForUpdate(email).orElse(null);
        if (user == null) {
            return;
        }

        byte[] bytes = new byte[32];
        RESET_TOKEN_RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        user.setResetToken(resetTokenDigest(token));
        user.setResetTokenExpiry(LocalDateTime.now().plusHours(24));
        userRepository.saveAndFlush(user);

        String resetLink = frontendUrl + "/reset-password?token=" + token;
        try {
            emailService.sendPasswordResetEmail(user.getEmail(), resetLink);
        } catch (Exception e) {
            // 邮件异常可能包含发送内容，不能将重置链接写入日志或响应。
            log.warn("密码重置邮件发送失败: userId={}", user.getId());
            throw new RuntimeException("发送重置密码邮件失败，请稍后重试");
        }
    }

    @Override
    @Transactional
    public void resetPassword(PasswordResetRequest request) {
        if (request.getNewPassword() == null || request.getNewPassword().isBlank()
                || request.getNewPassword().length() < 6) {
            throw new CredentialValidationException("新密码长度不能少于6个字符");
        }
        String token = request.getToken();
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            throw new CredentialValidationException("重置链接无效或已过期，请重新申请");
        }
        String digest = resetTokenDigest(token);
        User user = userRepository.findByResetToken(digest)
                .orElseThrow(() -> new CredentialValidationException("重置链接无效或已过期，请重新申请"));
        if (!digest.equals(user.getResetToken()) || user.getResetTokenExpiry() == null
                || !user.getResetTokenExpiry().isAfter(LocalDateTime.now())) {
            throw new CredentialValidationException("重置链接无效或已过期，请重新申请");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.saveAndFlush(user);
        log.info("密码重置成功: userId={}", user.getId());
    }

    private static String resetTokenDigest(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("无法生成密码重置令牌");
        }
    }

    private static String publicRegistrationRole(String role) {
        if (role != null && !role.matches("(?i)\\s*(?:(?:ROLE_)?(?:USER|HOST))?\\s*")) {
            throw new CredentialValidationException("公开注册仅支持普通用户或房东");
        }
        String normalized;
        try {
            normalized = AccountRole.normalize(role == null || role.isBlank() ? UserRole.ROLE_USER.name() : role);
        } catch (IllegalArgumentException e) {
            throw new CredentialValidationException("公开注册仅支持普通用户或房东");
        }
        if (!UserRole.ROLE_USER.name().equals(normalized) && !UserRole.ROLE_HOST.name().equals(normalized)) {
            throw new CredentialValidationException("公开注册仅支持普通用户或房东");
        }
        return normalized;
    }

    // 头像上传功能已迁移到FileController统一处理
    // 请使用 /api/files/upload?type=avatar 端点
    
    /**
     * 获取当前认证用户
     * @return 当前用户
     */
    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));
    }

    private UserDTO convertToDTO(User user) {
        return userMapper.toDTO(user);
    }

    @Override
    public AuthResponse getUserInfo(String username) {
        log.info("获取用户信息: {}", username);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));

        // 使用构造函数确保所有字段正确设置，包括 role
        return new AuthResponse(user);
    }

    @Override
    @Transactional
    public void updateUserAvatar(String username, String avatarUrl) {
        log.info("更新用户头像: username={}, avatarUrl={}", username, avatarUrl);
        
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));
        
        user.setAvatar(avatarUrl);
        userRepository.save(user);
        
        log.info("用户头像更新成功: userId={}", user.getId());
    }

    /**
     * 检查用户名是否已存在
     * @param username 用户名
     * @return true如果存在，false如果不存在
     */
    @Override
    public boolean isUsernameExists(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        return userRepository.existsByUsername(username.trim());
    }

    /**
     * 检查邮箱是否已存在
     * @param email 邮箱
     * @return true如果存在，false如果不存在
     */
    @Override
    public boolean isEmailExists(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        return userRepository.existsByEmail(email.trim().toLowerCase());
    }

    /**
     * 为新注册用户生成JWT令牌，避免使用userDetailsService
     * @param user 新创建的用户
     * @return JWT令牌
     */
    private String generateTokenForNewUser(User user) {
        log.info("为新注册用户 {} 生成令牌", user.getUsername());
        return jwtTokenProvider.generateToken(user.getUsername(), user.getId(), user.getRole());
    }
} 