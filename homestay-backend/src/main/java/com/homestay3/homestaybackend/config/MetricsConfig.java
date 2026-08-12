package com.homestay3.homestaybackend.config;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * 指标埋点辅助（纯静态工具类，非 Spring Bean，无 CGLIB 代理问题）。
 *
 * <p>MeterRegistry 由 Micrometer 自动装配（无需手动建 registry），各埋点处直接注入即可。
 * 本类提供统一的静态计数辅助：registry 为空时静默跳过——
 * 部分纯 Mockito 单元测试直接 new 目标类（如 RateLimitAspect/三个 MQ Consumer），
 * 不会注入 MeterRegistry，null 防护保证这些测试不受埋点影响。
 */
public final class MetricsConfig {

    private MetricsConfig() {
    }

    /**
     * 原子计数 +1。registry 为 null 时静默跳过（单元测试场景）。
     */
    public static void increment(MeterRegistry registry, String name, String... tags) {
        if (registry == null) {
            return;
        }
        registry.counter(name, tags).increment();
    }
}
