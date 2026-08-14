package com.homestay3.homestaybackend.config;

import com.homestay3.homestaybackend.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthChannelInterceptor.class);

    private final JwtTokenProvider jwtTokenProvider;

    public WebSocketAuthChannelInterceptor(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticateConnect(accessor);
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            validateSubscription(accessor);
        }

        return message;
    }

    private void authenticateConnect(StompHeaderAccessor accessor) {
        // 1. 优先使用握手阶段（httpOnly Cookie）认证结果 —— 刷新页面后无内存 token 也能重连
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        Object handshakeUserId = sessionAttributes != null
                ? sessionAttributes.get(WebSocketCookieHandshakeInterceptor.ATTR_USER_ID)
                : null;

        if (handshakeUserId instanceof Number number) {
            Collection<? extends GrantedAuthority> authorities = parseAuthorities(
                    String.valueOf(sessionAttributes.getOrDefault(
                            WebSocketCookieHandshakeInterceptor.ATTR_AUTHORITIES, "")));
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(String.valueOf(number.longValue()), null, authorities);
            accessor.setUser(authentication);
            log.debug("Authenticated WebSocket connection via cookie handshake: userId={}", number.longValue());
            return;
        }

        // 2. 兜底：STOMP CONNECT header 中的 token（旧客户端 / 非 cookie 环境）
        String token = resolveToken(accessor);
        if (token == null || !jwtTokenProvider.validateToken(token)) {
            throw new AccessDeniedException("Invalid WebSocket token");
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(token);
        if (userId == null) {
            throw new AccessDeniedException("WebSocket token missing userId");
        }

        Collection<? extends GrantedAuthority> authorities = parseAuthorities(jwtTokenProvider.getAuthoritiesFromToken(token));
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(String.valueOf(userId), null, authorities);
        accessor.setUser(authentication);
        log.debug("Authenticated WebSocket connection for userId={}", userId);
    }

    private void validateSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            return;
        }

        if (destination.startsWith("/topic/notifications/") || destination.startsWith("/topic/unread-count/")) {
            throw new AccessDeniedException("Use user notification queues instead of direct notification topics");
        }

        if (destination.startsWith("/user/") && accessor.getUser() == null) {
            throw new AccessDeniedException("Authentication required for user queue subscription");
        }
    }

    private String resolveToken(StompHeaderAccessor accessor) {
        return Stream.of("Authorization", "authorization", "X-Authorization", "x-authorization")
                .map(accessor::getFirstNativeHeader)
                .filter(Objects::nonNull)
                .map(this::stripBearerPrefix)
                .filter(token -> !token.isBlank())
                .findFirst()
                .orElse(null);
    }

    private String stripBearerPrefix(String value) {
        String trimmed = value.trim();
        return trimmed.startsWith("Bearer ") ? trimmed.substring(7) : trimmed;
    }

    private Collection<? extends GrantedAuthority> parseAuthorities(String authorities) {
        if (authorities == null || authorities.isBlank()) {
            return List.of();
        }

        return Stream.of(authorities.split(","))
                .map(String::trim)
                .filter(authority -> !authority.isEmpty())
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }
}
