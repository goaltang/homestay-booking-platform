package com.homestay3.homestaybackend.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.homestay3.homestaybackend.entity.Homestay;
import com.homestay3.homestaybackend.entity.User;
import com.homestay3.homestaybackend.model.HomestayStatus;
import com.homestay3.homestaybackend.repository.HomestayRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 下单链路 API 测试
 *
 * 覆盖：找 ACTIVE 房源 → 未来日期下单（201，自动确认房源 CONFIRMED/PENDING）→
 * 按 id 查询订单订单号一致 → 非法日期下单返回 400。
 * 每个用例在 @BeforeEach 创建自己的房东 + ACTIVE 房源，并注册独立客人。
 */
class OrderApiTest extends ApiTestBase {

    @Autowired
    private HomestayRepository homestayRepository;

    private Long homestayId;

    @BeforeEach
    void setUp() {
        long ts = System.currentTimeMillis();
        // 创建房东用户（直接落库，不走注册接口，因为注册只给 ROLE_USER）
        User host = new User();
        host.setUsername("order_host_" + ts);
        host.setEmail("order_host_" + ts + "@test.com");
        host.setPassword(passwordEncoder.encode("test123456"));
        host.setRole("ROLE_HOST");
        host.setEnabled(true);
        host = userRepository.save(host);

        // 创建自动确认的 ACTIVE 房源
        Homestay homestay = new Homestay();
        homestay.setTitle("API 测试房源 " + ts);
        homestay.setType("公寓");
        homestay.setPrice(new BigDecimal("199.00"));
        homestay.setMaxGuests(4);
        homestay.setMinNights(1);
        homestay.setStatus(HomestayStatus.ACTIVE);
        homestay.setOwner(host);
        homestay.setAutoConfirm(true);
        homestay.setAddressDetail("深圳市南山区测试路 1 号");
        homestay.setProvinceCode("440000");
        homestay.setCityCode("440300");
        homestay.setProvinceText("广东省");
        homestay.setCityText("深圳市");
        homestay.setDistrictText("南山区");
        homestayRepository.save(homestay);
        homestayId = homestay.getId();
    }

    private Map<String, Object> buildOrderBody(Long hid, LocalDate checkIn, LocalDate checkOut) {
        Map<String, Object> body = new HashMap<>();
        body.put("homestayId", hid);
        body.put("checkInDate", checkIn.toString());
        body.put("checkOutDate", checkOut.toString());
        body.put("guestCount", 2);
        body.put("guestPhone", "13800138000");
        return body;
    }

    @Test
    @DisplayName("查找 ACTIVE 房源并用未来日期下单，201 且状态为 CONFIRMED/PENDING，按 id 可查回且订单号一致")
    void createOrder_withFutureDates_shouldReturn201AndConfirmed() throws Exception {
        // 1. 通过列表接口取第一个 ACTIVE 房源（若列表接口拿不到则回退到 @BeforeEach 创建的房源）
        ResponseEntity<String> listResp = restTemplate.getForEntity(
                baseUrl() + "/api/homestays?page=0&size=5", String.class);
        assertThat(listResp.getStatusCodeValue()).isEqualTo(200);
        JsonNode list = parse(listResp);
        Long picked = null;
        for (JsonNode item : list.path("data")) {
            if ("ACTIVE".equals(item.path("status").asText())) {
                picked = item.path("id").asLong();
                break;
            }
        }
        if (picked != null) {
            homestayId = picked;
        }
        assertThat(homestayId).as("应存在至少一个 ACTIVE 房源").isNotNull();

        // 2. 注册客人并登录
        JsonNode reg = registerUser("order_guest");
        String token = extractToken(reg);

        // 3. 未来日期下单（+30 天，避开日期约束）
        LocalDate checkIn = LocalDate.now().plusDays(30);
        LocalDate checkOut = checkIn.plusDays(2);
        ResponseEntity<String> resp = postJson("/api/orders", buildOrderBody(homestayId, checkIn, checkOut),
                authHeaders(token));
        assertThat(resp.getStatusCodeValue()).isEqualTo(201);
        JsonNode order = parse(resp);
        assertThat(order.path("status").asText()).as("自动确认房源下单应 CONFIRMED 或 PENDING")
                .isIn("CONFIRMED", "PENDING");
        String orderNumber = order.path("orderNumber").asText();
        long orderId = order.path("id").asLong();
        assertThat(orderNumber).isNotBlank();
        assertThat(orderId).isPositive();

        // 4. GET /api/orders/{id} → 200，订单号一致
        ResponseEntity<String> getResp = restTemplate.exchange(
                baseUrl() + "/api/orders/" + orderId,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                String.class);
        assertThat(getResp.getStatusCodeValue()).isEqualTo(200);
        JsonNode fetched = parse(getResp);
        assertThat(fetched.path("orderNumber").asText()).isEqualTo(orderNumber);
    }

    @Test
    @DisplayName("非法日期下单（入住日期晚于退房日期）应返回 400")
    void createOrder_withInvalidDates_shouldReturn400() throws Exception {
        JsonNode reg = registerUser("order_bad");
        String token = extractToken(reg);

        LocalDate checkIn = LocalDate.now().plusDays(30);
        LocalDate checkOut = checkIn.minusDays(1); // 退房早于入住，触发参数校验
        ResponseEntity<String> resp = postJson("/api/orders", buildOrderBody(homestayId, checkIn, checkOut),
                authHeaders(token));
        assertThat(resp.getStatusCodeValue()).isEqualTo(400);
    }
}
