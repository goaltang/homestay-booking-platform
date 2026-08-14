package com.homestay3.homestaybackend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    
    // 免登录路径列表
    private static final List<String> PUBLIC_PATHS = Arrays.asList(
        "/api/admin/auth/login",
        "/api/auth/register",
        "/api/auth/login",
        "/api/auth/forgot-password",
        "/api/auth/reset-password",
        "/api/auth/check-username",
        "/api/auth/check-email",
        "/swagger-ui",
        "/v3/api-docs",
        "/swagger-resources"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestMethod = request.getMethod();
        String requestPath = request.getRequestURI();
        log.debug("处理请求: {} {}", requestMethod, requestPath);

        // 检查是否为公开路径 (传入方法)
        if (isPublicPath(requestPath, requestMethod)) {
            log.debug("公开路径: {} {}, 跳过JWT验证", requestMethod, requestPath);
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String jwt = getJwtFromRequest(request);
            log.debug("JWT Token存在: {}", jwt != null);

            if (jwt != null && jwtTokenProvider.validateToken(jwt)) {
                String username = jwtTokenProvider.getUsernameFromToken(jwt);
                Long userId = jwtTokenProvider.getUserIdFromToken(jwt);
                // 从 token 中获取权限字符串
                String authoritiesString = jwtTokenProvider.getAuthoritiesFromToken(jwt);
                log.debug("JWT Token有效, 用户名: {}, userId: {}, 权限字符串: {}", username, userId, authoritiesString);

                if (userId != null && authoritiesString != null) {
                    // 解析权限字符串为 GrantedAuthority 列表
                    Collection<? extends GrantedAuthority> authorities = 
                        Arrays.stream(authoritiesString.split(","))
                              .filter(auth -> auth != null && !auth.trim().isEmpty())
                              .map(SimpleGrantedAuthority::new)
                              .collect(Collectors.toList());
                              
                    log.info("从Token解析出的权限: {}", authorities);

                    // 创建 CustomUserDetails，同时包含 userId 和 username
                    CustomUserDetails customUserDetails = new CustomUserDetails(
                            userId, username, null, authorities);

                    // 创建认证令牌，使用 CustomUserDetails 作为 principal
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            customUserDetails, // 使用 CustomUserDetails 作为 principal
                            null, 
                            authorities); // 使用从 Token 解析的权限
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // 设置安全上下文
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.info("认证成功 (来自Token): 用户={}, userId={}, 路径={}, 权限={}", 
                            username, userId, requestPath, authorities);
                } else {
                    log.warn("无法从 Token 中获取用户名或权限");
                }

            } else if (jwt != null) {
                log.warn("无效的JWT Token");
            } else {
                log.debug("请求中未包含JWT Token: {} {}", requestMethod, requestPath);
            }
        } catch (Exception e) {
            log.error("JWT认证错误: {}", e.getMessage(), e);
            // 不设置认证信息，由Spring Security的认证失败处理机制处理
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        // 1. 优先从 httpOnly Cookie 读取（防 XSS）
        String cookieToken = readTokenFromCookies(request);
        if (cookieToken != null && !cookieToken.isBlank()) {
            return cookieToken;
        }

        // 2. 兜底：从 Authorization header 读取（兼容旧客户端 / WebSocket）
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private String readTokenFromCookies(HttpServletRequest request) {
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (jakarta.servlet.http.Cookie cookie : cookies) {
            if ("homestay_token".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
    
    // 检查是否为公开路径 (接收方法参数)
    private boolean isPublicPath(String path, String method) {
        // 1. 先检查完全匹配的公开路径 (与方法无关)
        if (PUBLIC_PATHS.stream().anyMatch(path::startsWith)) {
            log.debug("匹配公开路径列表: {}", path);
            return true;
        }
        
        // 2. 检查特定路径模式 (与方法无关)
        if (path.contains("/uploads/") || 
            path.contains("/static/uploads/") ||
            path.startsWith("/api/locations/") || 
            path.startsWith("/api/v1/locations/") ||
            // 精确匹配公开的 homestay-types 路径，例如 categories
            path.equals("/api/homestay-types/categories") ||
            path.equals("/api/v1/homestay-types/categories") ||
            path.startsWith("/api/review/public/")) { 
            log.debug("匹配其他公开路径模式: {}", path);
            return true;
        }
        
        // 3. 检查文件相关API - 只允许GET方法公开访问（文件下载），POST需要认证（文件上传）
        if (method.equals("GET") && path.startsWith("/api/files/")) {
            log.debug("允许公开文件下载: GET {}", path);
            return true;
        }
        
        // 4. 允许所有 OPTIONS 请求 (CORS 预检)
        if ("OPTIONS".equals(method)) {
            log.debug("允许 OPTIONS 请求: {}", path);
            return true;
        }

        // 5. 检查 homestays 相关的公开 API (区分 GET)
        if (method.equals("GET")) { // 只检查 GET 方法
            if (path.equals("/api/homestays") || // GET 列表
                path.equals("/api/v1/homestays") ||
                path.startsWith("/api/homestays/featured") || // GET 推荐
                path.startsWith("/api/v1/homestays/featured") ||
                path.matches("/api/homestays/\\d+") || // GET 详情 by ID
                path.matches("/api/v1/homestays/\\d+") ||
                path.startsWith("/api/homestays/type/") || // GET by Type
                path.startsWith("/api/v1/homestays/type/") ||
                path.matches("/api/homestays/\\d+/amenities") || // GET amenities by homestay ID
                path.matches("/api/homestays/\\d+/unavailable-dates") || // GET unavailable-dates by homestay ID
                path.startsWith("/api/amenities/") || // 允许公开 GET 设施信息
                path.startsWith("/api/v1/amenities/")) { // 允许公开 GET v1 设施信息
                log.debug("匹配公开 GET API: {} {}", method, path);
                return true;
            }
        }
        
        // 6. 允许公开的 POST /search 和价格计算
        if (method.equals("POST") &&
           (path.equals("/api/homestays/search") || path.equals("/api/v1/homestays/search") ||
            path.equals("/api/homestays/map-search") || path.equals("/api/v1/homestays/map-search") ||
            path.equals("/api/homestays/map-clusters") || path.equals("/api/v1/homestays/map-clusters") ||
            path.equals("/api/homestays/nearby") || path.equals("/api/v1/homestays/nearby") ||
            path.equals("/api/homestays/landmark-search") || path.equals("/api/v1/homestays/landmark-search") ||
            path.equals("/api/orders/calculate-price"))) {
             log.debug("匹配公开 POST API: {} {}", method, path);
             return true;
        }

        // 其他所有路径和方法都需要认证
        log.debug("路径需要认证: {} {}", method, path);
        return false;
    }
} 
