package com.homestay3.homestaybackend.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homestay3.homestaybackend.HomestayBackendApplication;
import com.homestay3.homestaybackend.entity.User;
import com.homestay3.homestaybackend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.Map;

/**
 * API 测试基类
 *
 * 为四个 API 测试类提供公共能力：注册用户、用户登录、admin 登录兜底（repository 插入）、
 * 统一的 TestRestTemplate + JSON 请求封装。
 *
 * 测试环境约束（与 AGENTS.md 红线一致）：
 * - @ActiveProfiles("test") + H2 内存库（application-test.properties 已配好），绝不连接真实 MySQL；
 * - 每个测试类自给自足，使用唯一用户名，不依赖其他测试的残留数据；
 * - 测试代码中不使用 deleteAll/drop/truncate。
 */
@SpringBootTest(classes = HomestayBackendApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
@Import(ApiTestSupportConfig.class)
public abstract class ApiTestBase {

    protected static final Logger log = LoggerFactory.getLogger(ApiTestBase.class);

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    protected HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    protected HttpHeaders authHeaders(String token) {
        HttpHeaders headers = jsonHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    protected ResponseEntity<String> postJson(String path, Map<String, Object> body, HttpHeaders headers) {
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        return restTemplate.postForEntity(baseUrl() + path, entity, String.class);
    }

    protected JsonNode parse(ResponseEntity<String> resp) throws Exception {
        return objectMapper.readTree(resp.getBody());
    }

    protected String extractToken(JsonNode body) {
        return body.path("token").asText();
    }

    protected long extractUserId(JsonNode body) {
        JsonNode user = body.path("user");
        if (user.isObject() && user.hasNonNull("id")) {
            return user.path("id").asLong();
        }
        return body.path("id").asLong();
    }

    /**
     * 注册一个新用户（唯一用户名），返回注册响应。
     */
    protected JsonNode registerUser(String prefix) throws Exception {
        String username = prefix + "_" + System.currentTimeMillis();
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("email", username + "@test.com");
        body.put("password", "test123456");
        body.put("fullName", "API 测试用户");
        ResponseEntity<String> resp = postJson("/api/auth/register", body, jsonHeaders());
        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("注册失败: " + resp.getStatusCode() + " body=" + resp.getBody());
        }
        return parse(resp);
    }

    /**
     * 用户登录，返回登录响应。
     */
    protected JsonNode login(String username, String password) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("password", password);
        ResponseEntity<String> resp = postJson("/api/auth/login", body, jsonHeaders());
        return parse(resp);
    }

    /**
     * 保障 admin 可用并返回 token。
     * 说明：AdminServiceImpl 的种子 CommandLineRunner 标注 @Profile("!test")，
     * 测试环境下默认不会自动创建 admin，因此先尝试 admin/admin888 登录，
     * 失败则通过 UserRepository 直接插入 admin（BCrypt 加密密码）后重新登录。
     */
    protected String ensureAdminToken() throws Exception {
        ResponseEntity<String> tryLogin = postJson("/api/admin/auth/login",
                Map.of("username", "admin", "password", "admin888"), jsonHeaders());
        if (tryLogin.getStatusCode().is2xxSuccessful()) {
            log.info("admin 账号在测试环境 seed 成功，直接登录");
            return extractToken(parse(tryLogin));
        }
        log.warn("admin 登录失败 (status={})，改为通过 UserRepository 插入 admin 用户", tryLogin.getStatusCode());
        if (!userRepository.existsByUsername("admin")) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setEmail("admin@homestay.local");
            admin.setPassword(passwordEncoder.encode("admin888"));
            admin.setRole("ROLE_ADMIN");
            admin.setEnabled(true);
            userRepository.save(admin);
        }
        ResponseEntity<String> resp = postJson("/api/admin/auth/login",
                Map.of("username", "admin", "password", "admin888"), jsonHeaders());
        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("插入 admin 后登录仍失败: " + resp.getStatusCode() + " body=" + resp.getBody());
        }
        return extractToken(parse(resp));
    }
}
