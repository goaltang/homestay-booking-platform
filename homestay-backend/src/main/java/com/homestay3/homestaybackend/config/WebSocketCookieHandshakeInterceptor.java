package com.homestay3.homestaybackend.config;

import com.homestay3.homestaybackend.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手拦截器：优先从 httpOnly Cookie 认证（token 迁移后刷新页面仍可重连）
 * 认证结果存入 session attributes，STOMP CONNECT 阶段直接复用
 */
@Component
public class WebSocketCookieHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketCookieHandshakeInterceptor.class);
    public static final String ATTR_USER_ID = "authenticatedUserId";
    public static final String ATTR_USERNAME = "authenticatedUsername";
    public static final String ATTR_AUTHORITIES = "authenticatedAuthorities";

    private final JwtTokenProvider jwtTokenProvider;

    public WebSocketCookieHandshakeInterceptor(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = readTokenFromCookie(request);
        if (token == null || token.isBlank() || !jwtTokenProvider.validateToken(token)) {
            // 无有效 cookie，交给 STOMP CONNECT 阶段的 header 认证兜底
            return true;
        }

        try {
            Long userId = jwtTokenProvider.getUserIdFromToken(token);
            String username = jwtTokenProvider.getUsernameFromToken(token);
            String authorities = jwtTokenProvider.getAuthoritiesFromToken(token);

            if (userId == null || username == null) {
                return true;
            }

            attributes.put(ATTR_USER_ID, userId);
            attributes.put(ATTR_USERNAME, username);
            attributes.put(ATTR_AUTHORITIES, authorities == null ? "" : authorities);
            log.debug("WebSocket 握手通过 Cookie 认证: userId={}, username={}", userId, username);
        } catch (Exception e) {
            log.warn("WebSocket 握手 Cookie 解析失败: {}", e.getMessage());
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    private String readTokenFromCookie(ServerHttpRequest request) {
        String cookieHeader = request.getHeaders().getFirst("Cookie");
        if (cookieHeader == null) {
            return null;
        }
        for (String part : cookieHeader.split(";")) {
            String pair = part.trim();
            if (pair.startsWith("homestay_token=")) {
                return pair.substring("homestay_token=".length());
            }
        }
        return null;
    }
}
