package com.homestay3.homestaybackend.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通知链路 API 测试（沉淀通知冒烟）
 *
 * 覆盖：admin 发系统通知（降级直接调用）→ 用户列表可见 → 用户标记已读。
 */
class NotificationApiTest extends ApiTestBase {

    @Test
    @DisplayName("admin 发系统通知 → 用户列表包含该通知 → 用户标记已读成功")
    void adminSendNotification_thenUserListAndRead_shouldWork() throws Exception {
        // 1. 注册用户 A 并登录
        JsonNode reg = registerUser("notify_user");
        String userToken = extractToken(reg);
        long userId = extractUserId(reg);

        // 2. admin 登录（seed 失败则 repository 插入）
        String adminToken = ensureAdminToken();

        // 3. admin 发系统通知
        String content = "API测试通知_" + System.currentTimeMillis();
        Map<String, Object> body = new HashMap<>();
        body.put("userId", userId);
        body.put("content", content);
        ResponseEntity<String> sendResp = postJson("/api/admin/notifications/send", body, authHeaders(adminToken));
        assertThat(sendResp.getStatusCodeValue()).isEqualTo(200);
        JsonNode sent = parse(sendResp);
        long notificationId = sent.path("id").asLong();
        assertThat(notificationId).as("发送系统通知应返回通知 id").isPositive();

        // 4. 用户 A 查询通知列表，应包含刚发送的通知
        ResponseEntity<String> listResp = restTemplate.exchange(
                baseUrl() + "/api/notifications?page=0&size=5",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(userToken)),
                String.class);
        assertThat(listResp.getStatusCodeValue()).isEqualTo(200);
        JsonNode list = parse(listResp);
        boolean found = false;
        for (JsonNode n : list.path("content")) {
            if (content.equals(n.path("content").asText())) {
                found = true;
                break;
            }
        }
        assertThat(found).as("通知列表应包含刚发送的通知内容").isTrue();

        // 5. 用户 A 标记已读
        ResponseEntity<String> readResp = restTemplate.exchange(
                baseUrl() + "/api/notifications/" + notificationId + "/read",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(userToken)),
                String.class);
        assertThat(readResp.getStatusCodeValue()).isEqualTo(200);
        JsonNode readBody = parse(readResp);
        // NotificationDTO 字段 isRead：Jackson 序列化键可能为 read 或 isRead，两种都兼容
        boolean readFlag = readBody.path("read").asBoolean(false) || readBody.path("isRead").asBoolean(false);
        assertThat(readFlag).as("标记已读后通知 read 应为 true").isTrue();
    }
}
