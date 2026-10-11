package com.homestay3.homestaybackend.security;

import com.homestay3.homestaybackend.entity.AuthSession;
import com.homestay3.homestaybackend.entity.User;
import com.homestay3.homestaybackend.repository.AuthSessionRepository;
import com.homestay3.homestaybackend.repository.UserRepository;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {
    private final UserRepository userRepository;
    private final AuthSessionRepository sessionRepository;

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpirationInMs;

    private Key getSigningKey() {
        if (jwtSecret == null || jwtSecret.isBlank() || jwtSecret.length() < 32) {
            throw new IllegalStateException("JWT 密钥未配置或长度不足（至少32字符）");
        }
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    @Transactional
    public String generateToken(Authentication authentication) {
        UserDetails principal = (UserDetails) authentication.getPrincipal();
        User user = userRepository.findByUsername(principal.getUsername())
                .orElseThrow(() -> new BadCredentialsException("用户不存在"));
        String role = AccountRole.normalize(user.getRole());
        boolean sameRole = principal.getAuthorities().size() == 1
                && principal.getAuthorities().iterator().next().getAuthority().equals(role);
        if (principal.getPassword() == null || !principal.getPassword().equals(user.getPassword()) || !sameRole) {
            throw new BadCredentialsException("认证期间凭证已变更，请重新登录");
        }
        return issueToken(user);
    }

    @Transactional
    public String generateToken(String username, String authorities) {
        return generateToken(username, null, authorities);
    }

    @Transactional
    public String generateToken(String username, Long userId, String authorities) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("用户不存在"));
        if (!user.isEnabled() || (userId != null && !userId.equals(user.getId()))) {
            throw new BadCredentialsException("账号不可用");
        }
        return issueToken(user);
    }

    private String issueToken(User user) {
        if (!user.isEnabled()) {
            throw new BadCredentialsException("账号不可用");
        }
        String role = AccountRole.normalize(user.getRole());
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(jwtExpirationInMs);
        String sessionId = UUID.randomUUID().toString();
        sessionRepository.deleteByExpiresAtBefore(now);
        sessionRepository.save(new AuthSession(sessionId, user.getId(), credentialDigest(user.getPassword()), expiry));
        return Jwts.builder()
                .setId(sessionId)
                .setSubject(user.getUsername())
                .claim("userId", user.getId())
                .claim("authorities", role)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiry))
                .signWith(getSigningKey())
                .compact();
    }

    /** HTTP 和 WebSocket 共同校验持久会话与当前账号，权限只取当前数据库。 */
    public CustomUserDetails authenticateToken(String token) {
        try {
            Claims claims = parse(token);
            if (claims.getId() == null) {
                throw new BadCredentialsException("缺少登录会话");
            }
            AuthSession session = sessionRepository.findById(claims.getId())
                    .orElseThrow(() -> new BadCredentialsException("登录会话已失效"));
            User user = userRepository.findById(session.getUserId())
                    .orElseThrow(() -> new BadCredentialsException("账号不存在"));
            Object claimUserId = claims.get("userId");
            if (!user.isEnabled() || !session.getExpiresAt().isAfter(Instant.now())
                    || !(claimUserId instanceof Number number) || number.longValue() != user.getId()
                    || !user.getUsername().equals(claims.getSubject())
                    || !AccountRole.normalize(user.getRole()).equals(claims.get("authorities", String.class))
                    || !MessageDigest.isEqual(session.getCredentialDigest().getBytes(StandardCharsets.UTF_8),
                            credentialDigest(user.getPassword()).getBytes(StandardCharsets.UTF_8))) {
                throw new BadCredentialsException("登录会话已失效");
            }
            return new CustomUserDetails(user.getId(), user.getUsername(), null,
                    List.of(new SimpleGrantedAuthority(AccountRole.normalize(user.getRole()))), user.isEnabled());
        } catch (JwtException | IllegalArgumentException e) {
            throw new BadCredentialsException("无效的登录凭证", e);
        }
    }

    @Transactional
    public void revokeToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        try {
            Claims claims = parse(token);
            if (claims.getId() != null) {
                sessionRepository.deleteById(claims.getId());
            }
        } catch (JwtException | IllegalArgumentException ignored) {
            // 过期或格式错误的凭证已不可用于认证，退出仍可清理客户端。
        }
    }

    public boolean validateToken(String token) {
        try {
            authenticateToken(token);
            return true;
        } catch (BadCredentialsException e) {
            return false;
        }
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            return authenticateToken(token).getUsername().equals(userDetails.getUsername());
        } catch (BadCredentialsException e) {
            return false;
        }
    }

    public String getUsernameFromToken(String token) {
        return authenticateToken(token).getUsername();
    }

    public Long getUserIdFromToken(String token) {
        return authenticateToken(token).getUserId();
    }

    public String getAuthoritiesFromToken(String token) {
        return authenticateToken(token).getAuthorities().iterator().next().getAuthority();
    }

    public long getExpirationSeconds() {
        return jwtExpirationInMs / 1000;
    }

    private Claims parse(String token) {
        return Jwts.parser().setSigningKey(getSigningKey()).build().parseClaimsJws(token).getBody();
    }

    private String credentialDigest(String password) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(password.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
