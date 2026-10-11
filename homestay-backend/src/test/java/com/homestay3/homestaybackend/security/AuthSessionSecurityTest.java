package com.homestay3.homestaybackend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.homestay3.homestaybackend.config.WebSocketAuthChannelInterceptor;
import com.homestay3.homestaybackend.config.WebSocketCookieHandshakeInterceptor;
import com.homestay3.homestaybackend.entity.User;
import com.homestay3.homestaybackend.repository.AuthSessionRepository;
import com.homestay3.homestaybackend.repository.UserRepository;
import com.homestay3.homestaybackend.service.UserService;
import com.homestay3.homestaybackend.dto.PasswordChangeRequest;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth_sessions_regression;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "jwt.expiration=3600000", "app.auth.cookie.secure=true"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class AuthSessionSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired AuthSessionRepository sessions;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserService userService;
    @org.springframework.boot.test.mock.mockito.MockBean
    com.homestay3.homestaybackend.service.EmailService emailService;
    @Autowired WebSocketAuthChannelInterceptor websocket;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;
    User guest;
    User admin;
    String guestToken;
    String adminToken;

    @BeforeEach
    void setup() {
        guest = account("USER");
        admin = account("ADMIN");
        guestToken = tokens.generateToken(guest.getUsername(), guest.getId(), "ROLE_ADMIN");
        adminToken = tokens.generateToken(admin.getUsername(), admin.getId(), "ROLE_ADMIN");
    }

    private User account(String role) {
        String name = UUID.randomUUID().toString().substring(0, 16);
        return users.saveAndFlush(User.builder().username(name).email(name + "@test.invalid")
                .password(encoder.encode("password123")).role(role).enabled(true).build());
    }

    @Test
    void profileAndPasswordEndpointsRequireIdentityAndPasswordChangeRevokesCookie() throws Exception {
        mvc.perform(put("/api/auth/profile").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/change-password").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/dashboard")).andExpect(status().isUnauthorized());
        Cookie cookie = new Cookie("homestay_token", guestToken);
        mvc.perform(post("/api/auth/change-password").cookie(cookie).header("Origin", "http://localhost:5173")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("oldPassword", "password123", "newPassword", "changed123"))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/change-password").cookie(cookie).header("Origin", "http://localhost:5173")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("oldPassword", "password123", "newPassword", "changed123",
                        "confirmPassword", "changed123"))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/current").cookie(cookie)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("username", guest.getUsername(), "password", "changed123"))))
                .andExpect(status().isOk());
    }

    @Test
    void infoRequiresRealAdminAndNeverContainsPassword() throws Exception {
        mvc.perform(get("/api/admin/auth/info")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/auth/info").header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/auth/info").cookie(new Cookie("homestay_token", adminToken)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/auth/info").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(admin.getId()))
                .andExpect(jsonPath("$.role").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$.password").doesNotExist());
        mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("username", admin.getUsername(), "password", "password123"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.admin.id").value(admin.getId()))
                .andExpect(jsonPath("$.admin.role").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$.admin.password").doesNotExist());
    }

    @Test
    void publicLoginAndRegisterSkipJwtButStillRequireCookieRequestSource() throws Exception {
        Cookie obsolete = new Cookie("homestay_token", "obsolete-jwt");
        for (String path : new String[]{"/api/auth/login", "/api/auth/register"}) {
            mvc.perform(post(path).cookie(obsolete).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
            mvc.perform(post(path).cookie(obsolete).header("Origin", "https://evil.invalid")
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/auth/login").cookie(obsolete).header("Origin", "http://localhost:5173")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("username", guest.getUsername(), "password", "password123"))))
                .andExpect(status().isOk());
    }

    @Test
    void loginRegisterHostUpgradeAndLogoutShareCookieAttributes() throws Exception {
        var login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("username", guest.getUsername(), "password", "password123"))))
                .andExpect(status().isOk()).andReturn();
        assertCookie(login.getResponse().getHeader("Set-Cookie"), 3600);
        String name = UUID.randomUUID().toString().substring(0, 16);
        var registration = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("username", name, "email", name + "@test.invalid",
                        "password", "password123", "role", "ROLE_USER"))))
                .andExpect(status().isOk()).andReturn();
        assertCookie(registration.getResponse().getHeader("Set-Cookie"), 3600);
        var upgrade = mvc.perform(post("/api/host/register").cookie(new Cookie("homestay_token", guestToken))
                .header("Origin", "http://localhost:5173")).andExpect(status().isOk()).andReturn();
        assertCookie(upgrade.getResponse().getHeader("Set-Cookie"), 3600);
        String fresh = mapper.readTree(upgrade.getResponse().getContentAsString()).path("token").asText();
        assertThat(tokens.validateToken(guestToken)).isFalse();
        assertThat(tokens.authenticateToken(fresh).getAuthorities()).extracting("authority").containsExactly("ROLE_HOST");
        var logout = mvc.perform(post("/api/auth/logout").cookie(new Cookie("homestay_token", fresh))
                .header("Origin", "http://localhost:5174")).andExpect(status().isOk()).andReturn();
        assertCookie(logout.getResponse().getHeader("Set-Cookie"), 0);
    }

    private void assertCookie(String cookie, long seconds) {
        assertThat(cookie).contains("homestay_token=", "Path=/", "Max-Age=" + seconds, "HttpOnly", "Secure", "SameSite=Lax");
    }

    @Test
    void bearerWinsCookieAndBadBearerDoesNotFallBack() throws Exception {
        Cookie cookie = new Cookie("homestay_token", guestToken);
        mvc.perform(get("/api/admin/auth/info").cookie(cookie).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/current").cookie(cookie).header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disableAndDowngradeApplyToExistingJwt() throws Exception {
        admin.setRole("USER"); users.saveAndFlush(admin);
        mvc.perform(get("/api/admin/auth/info").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isUnauthorized());
        assertThat(tokens.validateToken(adminToken)).isFalse();
        admin.setEnabled(false); users.saveAndFlush(admin);
        mvc.perform(get("/api/admin/auth/info").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> tokens.authenticateToken(adminToken)).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void logoutRevokesOnlySelectedSessionAndKeepsOtherFrontend() throws Exception {
        String secondAdmin = tokens.generateToken(admin.getUsername(), admin.getId(), "ROLE_ADMIN");
        mvc.perform(post("/api/admin/auth/logout").cookie(new Cookie("homestay_token", guestToken))
                .header("Authorization", "Bearer " + adminToken)).andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"));
        assertThat(tokens.validateToken(adminToken)).isFalse();
        assertThat(tokens.validateToken(secondAdmin)).isTrue();
        assertThat(tokens.validateToken(guestToken)).isTrue();
        mvc.perform(post("/api/auth/logout").cookie(new Cookie("homestay_token", guestToken))
                .header("Origin", "http://localhost:5173").header("Authorization", "Bearer " + secondAdmin))
                .andExpect(status().isOk()).andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
        assertThat(tokens.validateToken(guestToken)).isFalse();
        assertThat(tokens.validateToken(secondAdmin)).isTrue();
    }

    @Test
    void persistedPasswordChangesInvalidateAllSessions() {
        String second = tokens.generateToken(guest.getUsername(), guest.getId(), "ROLE_USER");
        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setOldPassword("password123"); request.setNewPassword("changed123");
        userService.changePassword(request, guest.getUsername());
        users.flush();
        assertThat(tokens.validateToken(guestToken)).isFalse();
        assertThat(tokens.validateToken(second)).isFalse();
        String fresh = tokens.generateToken(guest.getUsername(), guest.getId(), "ROLE_USER");
        userService.resetUserPassword(guest.getId()); users.flush();
        assertThat(tokens.validateToken(fresh)).isFalse();
    }

    @Test
    void authenticationSnapshotCannotMintSessionAfterConcurrentPasswordReset() {
        CustomUserDetails authenticated = new CustomUserDetails(guest.getId(), guest.getUsername(),
                guest.getPassword(), java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")));
        var authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                authenticated, null, authenticated.getAuthorities());
        guest.setRole("ROLE_ADMIN"); users.saveAndFlush(guest);
        assertThatThrownBy(() -> tokens.generateToken(authentication)).isInstanceOf(BadCredentialsException.class);
        guest.setRole("ROLE_USER"); guest.setPassword(encoder.encode("reset12345")); users.saveAndFlush(guest);
        assertThatThrownBy(() -> tokens.generateToken(authentication)).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void landlordAliasRemainsHostAndAdminLoginFailuresAre401() throws Exception {
        guest.setRole("ROLE_LANDLORD"); users.saveAndFlush(guest);
        String host = tokens.generateToken(guest.getUsername(), guest.getId(), "ROLE_LANDLORD");
        assertThat(tokens.authenticateToken(host).getAuthorities()).extracting("authority").containsExactly("ROLE_HOST");
        for (String password : new String[]{"password123", "wrong"}) {
            mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(Map.of("username", guest.getUsername(), "password", password))))
                    .andExpect(status().isUnauthorized());
        }
        admin.setEnabled(false); users.saveAndFlush(admin);
        mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("username", admin.getUsername(), "password", "password123"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidRoleAndLegacyTokensAreRejected() {
        guest.setRole("NOT_A_ROLE"); users.saveAndFlush(guest);
        assertThat(tokens.validateToken(guestToken)).isFalse();
        String legacy = Jwts.builder().setSubject(admin.getUsername()).claim("userId", admin.getId())
                .claim("authorities", "ROLE_ADMIN").setExpiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(Keys.hmacShaKeyFor("test-only-secret-for-h2-tests-0123456789abcdef".getBytes(StandardCharsets.UTF_8)))
                .compact();
        assertThat(tokens.validateToken(legacy)).isFalse();
    }

    @Test
    void roleUpgradeNeedsFreshTokenAndNewHostTokenWorks() throws Exception {
        guest.setRole("ROLE_HOST"); users.saveAndFlush(guest);
        assertThat(tokens.validateToken(guestToken)).isFalse();
        String host = tokens.generateToken(guest.getUsername(), guest.getId(), "ROLE_USER");
        assertThat(tokens.authenticateToken(host).getAuthorities()).extracting("authority").containsExactly("ROLE_HOST");
        guest.setRole("ROLE_ADMIN"); users.saveAndFlush(guest);
        mvc.perform(get("/api/admin/auth/info").header("Authorization", "Bearer " + host))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredSessionIsRejectedAndCleanedOnNextLogin() {
        String id = Jwts.parser().setSigningKey(Keys.hmacShaKeyFor(
                "test-only-secret-for-h2-tests-0123456789abcdef".getBytes(StandardCharsets.UTF_8)))
                .build().parseClaimsJws(guestToken).getBody().getId();
        var session = sessions.findById(id).orElseThrow();
        sessions.saveAndFlush(new com.homestay3.homestaybackend.entity.AuthSession(
                id, session.getUserId(), session.getCredentialDigest(), java.time.Instant.now().minusSeconds(1)));
        assertThat(tokens.validateToken(guestToken)).isFalse();
        tokens.generateToken(admin.getUsername(), admin.getId(), "ROLE_ADMIN");
        assertThat(sessions.findById(id)).isEmpty();
    }

    @Test
    void sessionsSurviveProviderRestartButRevocationDoesNot() {
        JwtTokenProvider restarted = new JwtTokenProvider(users, sessions);
        org.springframework.test.util.ReflectionTestUtils.setField(restarted, "jwtSecret",
                "test-only-secret-for-h2-tests-0123456789abcdef");
        assertThat(restarted.validateToken(guestToken)).isTrue();
        tokens.revokeToken(guestToken);
        assertThat(restarted.validateToken(guestToken)).isFalse();
    }

    @Test
    void cookieWriteRequiresTrustedOriginOrReferer() throws Exception {
        Cookie cookie = new Cookie("homestay_token", guestToken);
        mvc.perform(post("/api/auth/logout").cookie(cookie).header("Sec-Fetch-Site", "cross-site"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").cookie(cookie).header("Origin", "https://evil.invalid"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").cookie(cookie)).andExpect(status().isForbidden());
        for (String origin : new String[]{"null", "http://localhost.evil.invalid:5173", "https://evil.homestay3.com"}) {
            mvc.perform(post("/api/auth/logout").cookie(cookie).header("Origin", origin))
                    .andExpect(status().isForbidden());
        }
        assertThat(tokens.validateToken(guestToken)).isTrue();
        mvc.perform(post("/api/auth/logout").cookie(cookie).header("Referer", "http://localhost:5173/profile"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:8088", "http://127.0.0.1:8088", "http://homestay.test:18088"})
    void proxyPreservingHostAllowsUserLoginAndCookieLogout(String origin) throws Exception {
        // Nginx 必须转发浏览器的完整 Host；不能把外部端口替换为内部 80/8081。
        var login = mvc.perform(post(origin + "/api/auth/login").header("Origin", origin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("username", guest.getUsername(), "password", "password123"))))
                .andExpect(status().isOk()).andReturn();
        String token = mapper.readTree(login.getResponse().getContentAsString()).path("token").asText();
        mvc.perform(get(origin + "/api/auth/current").cookie(new Cookie("homestay_token", token)))
                .andExpect(status().isOk());
        mvc.perform(post(origin + "/api/auth/logout").header("Origin", origin)
                .cookie(new Cookie("homestay_token", token)))
                .andExpect(status().isOk());
        assertThat(tokens.validateToken(token)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:8089", "http://127.0.0.1:8089", "http://homestay.test:18089"})
    void proxyPreservingHostAllowsAdminLoginAndBearerLogout(String origin) throws Exception {
        var login = mvc.perform(post(origin + "/api/admin/auth/login").header("Origin", origin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("username", admin.getUsername(), "password", "password123"))))
                .andExpect(status().isOk()).andReturn();
        String token = mapper.readTree(login.getResponse().getContentAsString()).path("token").asText();
        mvc.perform(get(origin + "/api/admin/auth/info").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(post(origin + "/api/admin/auth/logout").header("Origin", origin)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(tokens.validateToken(token)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:8088", "http://localhost:8089", "http://homestay.test:18088"})
    void proxyPreservingHostAllowsSockJsSameOrigin(String origin) throws Exception {
        mvc.perform(get(origin + "/ws/info").header("Origin", origin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.websocket").value(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:8089", "https://evil.invalid", "null"})
    void proxyStillRejectsDifferentOriginsAndSpoofedForwardedHost(String origin) throws Exception {
        mvc.perform(post("http://localhost:8088/api/auth/logout").header("Origin", origin)
                .header("X-Forwarded-Host", origin.replace("http://", "").replace("https://", ""))
                .cookie(new Cookie("homestay_token", guestToken)))
                .andExpect(status().isForbidden());
        assertThat(tokens.validateToken(guestToken)).isTrue();
        mvc.perform(get("http://localhost:8088/ws/info").header("Origin", origin))
                .andExpect(status().isForbidden());
    }

    @Test
    void emailedResetConsumesTokenAndInvalidatesHttpAndConnectedWebSocket() throws Exception {
        websocket.preSend(frame(StompCommand.CONNECT, "emailed-reset", guestToken, null), null);
        mvc.perform(post("/api/auth/forgot-password").param("email", guest.getEmail()))
                .andExpect(status().isOk());
        var link = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(emailService).sendPasswordResetEmail(
                org.mockito.ArgumentMatchers.eq(guest.getEmail()), link.capture());
        String reset = java.net.URI.create(link.getValue()).getRawQuery().substring("token=".length());
        var request = mapper.writeValueAsString(Map.of("token", reset, "newPassword", "reset12345"));
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk());
        assertThat(encoder.matches("reset12345", users.findById(guest.getId()).orElseThrow().getPassword())).isTrue();
        assertThat(users.findById(guest.getId()).orElseThrow().getResetToken()).isNull();
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/auth/current").cookie(new Cookie("homestay_token", guestToken)))
                .andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> websocket.preSend(frame(StompCommand.SEND, "emailed-reset", null, null), null))
                .isInstanceOf(AccessDeniedException.class);
        websocket.preSend(frame(StompCommand.DISCONNECT, "emailed-reset", null, null), null);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("username", guest.getUsername(), "password", "password123"))))
                .andExpect(status().isUnauthorized());
        var login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("username", guest.getUsername(), "password", "reset12345"))))
                .andExpect(status().isOk()).andReturn();
        assertThat(tokens.validateToken(mapper.readTree(login.getResponse().getContentAsString()).path("token").asText())).isTrue();
    }

    private Message<byte[]> frame(StompCommand command, String session, String token, String cookieToken) {
        StompHeaderAccessor headers = command == null ? StompHeaderAccessor.createForHeartbeat() : StompHeaderAccessor.create(command);
        headers.setSessionId(session);
        headers.setSessionAttributes(new HashMap<>());
        if (token != null) headers.setNativeHeader("Authorization", "Bearer " + token);
        if (cookieToken != null) headers.getSessionAttributes().put(WebSocketCookieHandshakeInterceptor.ATTR_TOKEN, cookieToken);
        headers.setDestination("/user/queue/notifications");
        headers.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }

    @Test
    void websocketUsesSameRulesAndBlocksAlreadyConnectedSessionAfterLogout() {
        Message<byte[]> connect = frame(StompCommand.CONNECT, "auth-regression", adminToken, guestToken);
        websocket.preSend(connect, null);
        assertThat(StompHeaderAccessor.wrap(connect).getUser().getName()).isEqualTo(admin.getId().toString());
        assertThat(websocket.preSend(frame(null, "auth-regression", null, null), null)).isNotNull();
        tokens.revokeToken(adminToken);
        assertThatThrownBy(() -> websocket.preSend(frame(StompCommand.SEND, "auth-regression", null, null), null))
                .isInstanceOf(AccessDeniedException.class);
        SimpMessageHeaderAccessor outbound = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        outbound.setSessionId("auth-regression");
        assertThat(websocket.validateOutbound(MessageBuilder.createMessage(new byte[0], outbound.getMessageHeaders()))).isNull();
        websocket.preSend(frame(StompCommand.DISCONNECT, "auth-regression", null, null), null);
        websocket.preSend(frame(StompCommand.CONNECT, "guest-password", guestToken, null), null);
        guest.setPassword(encoder.encode("changed123")); users.saveAndFlush(guest);
        assertThatThrownBy(() -> websocket.preSend(frame(StompCommand.SUBSCRIBE, "guest-password", null, null), null))
                .isInstanceOf(AccessDeniedException.class);
        websocket.preSend(frame(StompCommand.DISCONNECT, "guest-password", null, null), null);
        String newGuest = tokens.generateToken(guest.getUsername(), guest.getId(), "ROLE_USER");
        websocket.preSend(frame(StompCommand.CONNECT, "guest-disabled", newGuest, null), null);
        guest.setEnabled(false); users.saveAndFlush(guest);
        assertThatThrownBy(() -> websocket.preSend(frame(StompCommand.SEND, "guest-disabled", null, null), null))
                .isInstanceOf(AccessDeniedException.class);
        websocket.preSend(frame(StompCommand.DISCONNECT, "guest-disabled", null, null), null);
        assertThatThrownBy(() -> websocket.preSend(frame(StompCommand.CONNECT, "disabled", null, guestToken), null))
                .isInstanceOf(AccessDeniedException.class);
    }
}
