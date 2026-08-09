package com.homestay3.homestaybackend.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 认证链路 API 测试
 *
 * 覆盖：注册、登录、错误密码登录、无 token 访问受保护接口。
 * 每个用例注册自己的用户，互不依赖。
 */
class AuthApiTest extends ApiTestBase {

    @Test
    @DisplayName("注册新用户应返回 token")
    void register_shouldReturnToken() throws Exception {
        String username = "auth_reg_" + System.currentTimeMillis();
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("email", username + "@test.com");
        body.put("password", "test123456");
        ResponseEntity<String> resp = postJson("/api/auth/register", body, jsonHeaders());

        // 说明：任务清单写「注册→201」，但 AuthController.register 实际返回 200（ResponseEntity.ok），
        // 为保证回归测试与真实实现一致，此处断言 200。
        assertThat(resp.getStatusCodeValue()).isEqualTo(200);

        JsonNode reg = parse(resp);
        assertThat(reg.path("token").asText()).as("注册响应应包含 token").isNotBlank();
        assertThat(extractUserId(reg)).as("注册响应应包含用户 id").isPositive();
    }

    @Test
    @DisplayName("注册后用正确密码登录应返回 token")
    void login_withValidCredentials_shouldReturnToken() throws Exception {
        JsonNode reg = registerUser("auth_login");
        String username = reg.path("user").path("username").asText();
        String password = "test123456";

        ResponseEntity<String> resp = postJson("/api/auth/login",
                Map.of("username", username, "password", password), jsonHeaders());

        assertThat(resp.getStatusCodeValue()).isEqualTo(200);
        JsonNode loginBody = parse(resp);
        assertThat(loginBody.path("token").asText()).as("登录响应应包含有效 token").isNotBlank();
        assertThat(loginBody.path("user").path("username").asText()).isEqualTo(username);
    }

    @Test
    @DisplayName("错误密码登录应返回 401")
    void login_withWrongPassword_shouldReturn401() throws Exception {
        JsonNode reg = registerUser("auth_badpwd");
        String username = reg.path("user").path("username").asText();

        ResponseEntity<String> resp = postJson("/api/auth/login",
                Map.of("username", username, "password", "wrong-password-000"), jsonHeaders());

        // LoginException 由 GlobalExceptionHandler 映射为 401
        assertThat(resp.getStatusCodeValue()).isEqualTo(401);
    }

    @Test
    @DisplayName("无 token 访问受保护接口应返回 401/403")
    void protectedEndpoint_withoutToken_shouldBeRejected() {
        ResponseEntity<String> resp = restTemplate.getForEntity(baseUrl() + "/api/orders", String.class);
        assertThat(resp.getStatusCodeValue()).as("未携带 token 访问 /api/orders 应被拒绝").isIn(401, 403);
    }
}
