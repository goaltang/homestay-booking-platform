package com.homestay3.homestaybackend.config;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 测试环境的 Redisson mock（替代真实 Redis 连接）。
 *
 * <p>test profile 下 redisson.enabled=false（application-test.properties），
 * 主 RedissonConfig 不装配，这里提供 Mock RedissonClient 供
 * BookingConflictService / PricingServiceImpl 等注入使用——CI 无 Redis 也能跑。
 *
 * <p>注意：本类放在 src/test，仅在测试 classpath 存在，不会进生产构建。
 * @Profile("test") + @Configuration 会被 @SpringBootTest 组件扫描自动发现
 * （@TestConfiguration 不行，必须显式 @Import）。
 */
@Configuration
@Profile("test")
public class RedissonTestConfig {

    @Bean
    @Primary
    public RedissonClient redissonClient() {
        RLock mockLock = mock(RLock.class);
        RLock mockMultiLock = mock(RLock.class);

        try {
            doReturn(true).when(mockLock).tryLock(anyLong(), anyLong(), any(TimeUnit.class));
            doReturn(true).when(mockMultiLock).tryLock(anyLong(), anyLong(), any(TimeUnit.class));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        doNothing().when(mockMultiLock).unlock();

        RedissonClient mockClient = mock(RedissonClient.class);
        when(mockClient.getLock(anyString())).thenReturn(mockLock);
        // varargs 方法用 any() 匹配整个数组参数
        when(mockClient.getMultiLock(any())).thenReturn(mockMultiLock);
        return mockClient;
    }
}
