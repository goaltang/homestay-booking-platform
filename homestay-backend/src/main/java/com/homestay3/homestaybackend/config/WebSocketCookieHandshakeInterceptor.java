package com.homestay3.homestaybackend.config;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import java.util.Map;

@Component
public class WebSocketCookieHandshakeInterceptor implements HandshakeInterceptor {
    public static final String ATTR_TOKEN = "authToken";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler handler, Map<String, Object> attributes) {
        String cookies = request.getHeaders().getFirst("Cookie");
        if (cookies != null) {
            for (String part : cookies.split(";")) {
                String pair = part.trim();
                if (pair.startsWith("homestay_token=")) {
                    // CONNECT及后续收发均重新校验；握手不缓存身份或旧权限。
                    attributes.put(ATTR_TOKEN, pair.substring("homestay_token=".length()));
                    break;
                }
            }
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler handler, Exception exception) {}
}
