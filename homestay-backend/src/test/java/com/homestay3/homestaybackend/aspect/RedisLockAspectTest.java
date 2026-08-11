package com.homestay3.homestaybackend.aspect;

import com.homestay3.homestaybackend.annotation.RedisLock;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * RedisLockAspect 单元测试（纯 Mockito，不依赖 Spring 上下文/数据库）。
 * 覆盖：拿锁失败跳过业务 / 拿锁成功执行并释放锁 / SpEL key 解析。
 */
@ExtendWith(MockitoExtension.class)
class RedisLockAspectTest {

    @Mock
    private com.homestay3.homestaybackend.util.RedisLock redisLockUtil;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    /** 测试用注解方法：key 引用参数 #orderNo */
    @RedisLock(key = "'order:' + #orderNo", timeoutSeconds = 30)
    public void lockedMethod(String orderNo) {
    }

    private RedisLock annotationOf() throws NoSuchMethodException {
        Method method = RedisLockAspectTest.class.getMethod("lockedMethod", String.class);
        return method.getAnnotation(RedisLock.class);
    }

    private void stubJoinPoint() throws NoSuchMethodException {
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(
                RedisLockAspectTest.class.getMethod("lockedMethod", String.class));
        when(joinPoint.getArgs()).thenReturn(new Object[]{"ORDER-1001"});
    }

    @Test
    void 获取锁失败时跳过业务方法并返回null() throws Throwable {
        RedisLockAspect aspect = new RedisLockAspect(redisLockUtil);
        stubJoinPoint();
        when(redisLockUtil.generateRequestId()).thenReturn("req-1");
        when(redisLockUtil.tryLock("order:ORDER-1001", "req-1", Duration.ofSeconds(30))).thenReturn(false);

        Object result = aspect.around(joinPoint, annotationOf());

        assertNull(result);
        verify(joinPoint, never()).proceed();
        verify(redisLockUtil, never()).unlock(any(), any());
    }

    @Test
    void 获取锁成功后执行业务并在finally释放锁() throws Throwable {
        RedisLockAspect aspect = new RedisLockAspect(redisLockUtil);
        stubJoinPoint();
        when(redisLockUtil.generateRequestId()).thenReturn("req-2");
        when(redisLockUtil.tryLock("order:ORDER-1001", "req-2", Duration.ofSeconds(30))).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("done");

        Object result = aspect.around(joinPoint, annotationOf());

        assertEquals("done", result);
        verify(joinPoint).proceed();
        verify(redisLockUtil).unlock("order:ORDER-1001", "req-2");
    }

    @Test
    void 业务抛异常时也释放锁() throws Throwable {
        RedisLockAspect aspect = new RedisLockAspect(redisLockUtil);
        stubJoinPoint();
        when(redisLockUtil.generateRequestId()).thenReturn("req-3");
        when(redisLockUtil.tryLock("order:ORDER-1001", "req-3", Duration.ofSeconds(30))).thenReturn(true);
        when(joinPoint.proceed()).thenThrow(new RuntimeException("业务失败"));

        try {
            aspect.around(joinPoint, annotationOf());
        } catch (RuntimeException expected) {
            // 预期异常
        }

        verify(redisLockUtil).unlock("order:ORDER-1001", "req-3");
    }
}
