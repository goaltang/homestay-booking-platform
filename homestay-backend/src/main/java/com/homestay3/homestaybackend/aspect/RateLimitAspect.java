package com.homestay3.homestaybackend.aspect;

import com.homestay3.homestaybackend.annotation.RateLimit;
import com.homestay3.homestaybackend.exception.RateLimitExceededException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 接口限流切面：拦截标注了 @RateLimit 的方法，使用 Redis Lua 固定窗口计数。
 * 计数超过 limit 抛 RateLimitExceededException（全局异常处理返回 429）；
 * Redis 不可用时降级放行（与 RedisLock 的降级原则一致），不阻塞业务。
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class RateLimitAspect {

    private final StringRedisTemplate stringRedisTemplate;

    private static final String LIMIT_PREFIX = "rate:limit:";

    /** Lua 脚本：INCR + 首次设置过期时间，原子完成固定窗口计数 */
    private static final String INCR_SCRIPT =
            "local c = redis.call('INCR', KEYS[1]); " +
            "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end; " +
            "return c";

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String key = LIMIT_PREFIX + resolveKey(joinPoint, rateLimit);
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(INCR_SCRIPT, Long.class);
            Long count = stringRedisTemplate.execute(script, List.of(key), String.valueOf(rateLimit.windowSeconds()));
            if (count != null && count > rateLimit.limit()) {
                log.warn("接口限流触发: key={}, count={}, limit={}", key, count, rateLimit.limit());
                throw new RateLimitExceededException("请求过于频繁，请稍后再试");
            }
        } catch (RateLimitExceededException e) {
            throw e;
        } catch (Exception e) {
            // Redis 不可用/执行异常时降级放行，不阻塞业务
            log.warn("限流检查异常（Redis可能不可用），降级放行: key={}, error={}", key, e.getMessage());
        }
        return joinPoint.proceed();
    }

    /**
     * 生成限流 key：注解显式 key 优先；为空时取 IP + 类名.方法名。
     */
    private String resolveKey(ProceedingJoinPoint joinPoint, RateLimit rateLimit) {
        if (rateLimit.key() != null && !rateLimit.key().isBlank()) {
            return rateLimit.key();
        }
        String ip = "unknown";
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            if (request != null) {
                ip = request.getRemoteAddr();
            }
        }
        String methodName = joinPoint.getSignature().getDeclaringTypeName() + "." + joinPoint.getSignature().getName();
        return ip + ":" + methodName;
    }
}
