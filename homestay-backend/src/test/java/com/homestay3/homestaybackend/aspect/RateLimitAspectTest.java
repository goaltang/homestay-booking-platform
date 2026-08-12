package com.homestay3.homestaybackend.aspect;

import com.homestay3.homestaybackend.annotation.RateLimit;
import com.homestay3.homestaybackend.exception.RateLimitExceededException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * RateLimitAspect 单元测试（纯 Mockito，不依赖 Spring 上下文/数据库/Redis）。
 * 覆盖：未超限放行 / 超限抛 RateLimitExceededException / Redis 异常降级放行。
 */
@ExtendWith(MockitoExtension.class)
class RateLimitAspectTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    /** 测试用注解方法：limit 2 次 / 60s 窗口，显式 key */
    @RateLimit(limit = 2, windowSeconds = 60, key = "test:limit")
    public void limitedMethod() {
    }

    private RateLimit annotationOf() throws NoSuchMethodException {
        Method method = RateLimitAspectTest.class.getMethod("limitedMethod");
        return method.getAnnotation(RateLimit.class);
    }

    private void stubJoinPoint() {
        // 埋点（homestay.ratelimit.blocked 的 method tag）会读取 joinPoint.getSignature()，
        // 即使显式 key 分支不经过 IP+方法名，也需要 stub signature 避免 NPE
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getDeclaringTypeName()).thenReturn("com.homestay3.homestaybackend.aspect.RateLimitAspectTest");
        when(methodSignature.getName()).thenReturn("limitedMethod");
    }

    @Test
    void 未超限时放行业务方法() throws Throwable {
        RateLimitAspect aspect = new RateLimitAspect(stringRedisTemplate);
        stubJoinPoint();
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), eq(List.of("rate:limit:test:limit")), eq("60")))
                .thenReturn(1L);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.around(joinPoint, annotationOf());

        assertEquals("ok", result);
        verify(joinPoint).proceed();
    }

    @Test
    void 超限时抛RateLimitExceededException且业务不执行() throws Throwable {
        RateLimitAspect aspect = new RateLimitAspect(stringRedisTemplate);
        stubJoinPoint();
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), eq(List.of("rate:limit:test:limit")), eq("60")))
                .thenReturn(3L); // 超过 limit=2

        assertThrows(RateLimitExceededException.class, () -> aspect.around(joinPoint, annotationOf()));
        verify(joinPoint, never()).proceed();
    }

    @Test
    void Redis异常时降级放行() throws Throwable {
        RateLimitAspect aspect = new RateLimitAspect(stringRedisTemplate);
        stubJoinPoint();
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), any(List.class), any(String.class)))
                .thenThrow(new RuntimeException("Redis 连接失败"));
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.around(joinPoint, annotationOf());

        assertEquals("ok", result);
        verify(joinPoint).proceed();
    }
}
