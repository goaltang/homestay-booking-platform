package com.homestay3.homestaybackend.observability;

import com.homestay3.homestaybackend.HomestayBackendApplication;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 可观测性测试（Actuator + Micrometer Prometheus）。
 *
 * 测试环境约束（与 AGENTS.md 红线一致）：
 * - @ActiveProfiles("test") + H2 内存库（application-test.properties 已配好），绝不连接真实 MySQL；
 * - 只读端点 + 注入 MeterRegistry 手动打点，不写任何数据库表。
 * - @AutoConfigureObservability：Spring Boot 3 的 @SpringBootTest 默认通过
 *   DisableObservabilityContextCustomizer 关闭指标导出（management.metrics.export.prometheus.enabled=false），
 *   不加此注解 /actuator/prometheus 会 404。显式开启后 Prometheus 端点才会注册。
 */
@SpringBootTest(classes = HomestayBackendApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@AutoConfigureObservability
@ActiveProfiles("test")
class ObservabilityMetricsTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    private String scrapePrometheus() {
        ResponseEntity<String> resp = restTemplate.getForEntity(
                "http://localhost:" + port + "/actuator/prometheus", String.class);
        assertThat(resp.getStatusCode().is2xxSuccessful())
                .as("GET /actuator/prometheus 应返回 2xx（SecurityConfig 已 permitAll 放行），实际=" + resp.getStatusCode())
                .isTrue();
        assertThat(resp.getBody()).as("prometheus 响应体不应为空").isNotNull();
        return resp.getBody();
    }

    @Test
    @DisplayName("GET /actuator/prometheus 应 200 且包含 JVM 指标（端点暴露验证）")
    void prometheusEndpoint_shouldReturn200AndExposeJvmMetrics() {
        String body = scrapePrometheus();
        // 端点暴露验证：JVM 指标（jvm_memory_used_bytes）由 Micrometer 自动导出
        assertThat(body).contains("jvm_memory");
    }

    @Test
    @DisplayName("限流 Counter 打点后应出现在 prometheus 输出中")
    void rateLimitCounter_shouldAppearInPrometheus() {
        meterRegistry.counter("homestay.ratelimit.blocked", "method", "com.example.FakeService.test")
                .increment();

        String body = scrapePrometheus();
        // prometheus 格式：指标名点转下划线，Counter 追加 _total
        assertThat(body).contains("homestay_ratelimit_blocked_total");
    }

    @Test
    @DisplayName("业务埋点（MQ 消费 / LLM 成败 / 首页统计耗时）应全部导出")
    void businessCountersAndTimer_shouldBeExported() {
        meterRegistry.counter("homestay.mq.consumed", "scenario", "order.timeout").increment();
        meterRegistry.counter("homestay.llm.requests", "result", "success").increment();
        meterRegistry.counter("homestay.llm.requests", "result", "failure").increment();
        meterRegistry.timer("homestay.home.stats.duration").record(Duration.ofMillis(5));

        String body = scrapePrometheus();
        assertThat(body)
                .contains("homestay_mq_consumed_total")
                .contains("homestay_llm_requests_total")
                .contains("homestay_home_stats_duration_seconds");
    }
}
