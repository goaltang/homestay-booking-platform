package com.homestay3.homestaybackend.config;

import com.homestay3.homestaybackend.security.CustomUserDetails;
import com.homestay3.homestaybackend.security.JwtTokenProvider;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {
    private final JwtTokenProvider jwtTokenProvider;
    // 仅记录连接对应凭证，撤销判断始终查询持久会话和当前账号。
    private final Map<String, String> connectionTokens = new ConcurrentHashMap<>();

    public WebSocketAuthChannelInterceptor(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        String sessionId = accessor.getSessionId();
        if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
            if (sessionId != null) connectionTokens.remove(sessionId);
            return message;
        }
        String token = sessionId == null ? null : connectionTokens.get(sessionId);
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authorization = accessor.getFirstNativeHeader("Authorization");
            if (authorization == null) authorization = accessor.getFirstNativeHeader("authorization");
            if (authorization != null) {
                token = authorization.startsWith("Bearer ") ? authorization.substring(7).trim() : authorization.trim();
            } else if (accessor.getSessionAttributes() != null) {
                token = (String) accessor.getSessionAttributes().get(WebSocketCookieHandshakeInterceptor.ATTR_TOKEN);
            }
        }
        try {
            CustomUserDetails user = jwtTokenProvider.authenticateToken(token);
            accessor.setUser(new UsernamePasswordAuthenticationToken(
                    String.valueOf(user.getUserId()), null, user.getAuthorities()));
            if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                connectionTokens.put(sessionId, token);
            }
        } catch (BadCredentialsException e) {
            throw new AccessDeniedException("WebSocket 登录已失效", e);
        }
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            if (destination != null && (destination.startsWith("/topic/notifications/")
                    || destination.startsWith("/topic/unread-count/"))) {
                throw new AccessDeniedException("Use user notification queues instead of direct notification topics");
            }
        }
        return message;
    }

    public Message<?> validateOutbound(Message<?> message) {
        String sessionId = SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
        String token = sessionId == null ? null : connectionTokens.get(sessionId);
        if (token != null && !jwtTokenProvider.validateToken(token)) {
            return null;
        }
        return message;
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        connectionTokens.remove(event.getSessionId());
    }
}
