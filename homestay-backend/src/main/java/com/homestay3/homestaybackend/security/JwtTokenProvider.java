package com.homestay3.homestaybackend.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Key;
import java.util.Date;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpirationInMs;

    private Key getSigningKey() {
        if (jwtSecret == null || jwtSecret.isBlank() || jwtSecret.length() < 32) {
            throw new IllegalStateException(
                    "JWT 密钥未配置或长度不足（HS256 要求至少 32 字符）。请通过环境变量 JWT_SECRET 注入，"
                    + "或在 application-prod.properties 中配置 jwt.secret=${JWT_SECRET}");
        }
        byte[] keyBytes = jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(Authentication authentication) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        
        // 获取用户的所有角色，以逗号分隔
        String authorities = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));
        
        log.info("为用户 {} 生成token, 权限: {}", userDetails.getUsername(), authorities);
        
        // 直接调用另一个generateToken方法，传入所有权限
        return generateToken(userDetails.getUsername(), authorities);
    }

    public String generateToken(String username, String authorities) {
        return generateToken(username, null, authorities);
    }

    public String generateToken(String username, Long userId, String authorities) {
        log.debug("生成JWT Token: 用户={}, userId={}", username, userId);
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationInMs);

        String token = Jwts.builder()
                .setSubject(username)
                .claim("authorities", authorities)
                .claim("userId", userId)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
        
        log.debug("JWT Token生成成功, 长度: {}", token.length());
        return token;
    }

    public String getUsernameFromToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            String username = claims.getSubject();

            return username;
        } catch (Exception e) {
            log.error("从Token解析用户名失败: {}", e.getMessage());
            throw e;
        }
    }

    public String getAuthoritiesFromToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            String authorities = claims.get("authorities", String.class);
            return authorities;
        } catch (Exception e) {
            log.error("从Token解析权限失败: {}", e.getMessage());
            return null;
        }
    }

    public Long getUserIdFromToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            Object userId = claims.get("userId");
            if (userId instanceof Number number) {
                return number.longValue();
            }
            if (userId instanceof String value && !value.isBlank()) {
                return Long.parseLong(value);
            }
            return null;
        } catch (Exception e) {
            log.error("从Token解析userId失败: {}", e.getMessage());
            return null;
        }
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);
            log.debug("JWT Token验证成功");
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.error("无效的JWT Token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 验证token是否有效（与UserDetails一起验证）
     * @param token JWT token
     * @param userDetails 用户信息
     * @return 是否有效
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = getUsernameFromToken(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        try {
            Claims claims = Jwts.parser()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }
}
