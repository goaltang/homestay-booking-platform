package com.homestay3.homestaybackend.aspect;

import com.homestay3.homestaybackend.annotation.RedisLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Duration;

/**
 * 分布式锁切面：拦截标注了 @RedisLock 的方法，统一处理加锁/解锁。
 * 必须 @Order(1)：确保在 @Transactional 事务开启前获取锁、事务提交后释放锁。
 * 锁 key 解析失败属于编码错误，直接抛 IllegalArgumentException。
 */
@Aspect
@Component
@Order(1)
@Slf4j
@RequiredArgsConstructor
public class RedisLockAspect {

    /** 注意与注解 RedisLock 同名，字段命名做避让 */
    private final com.homestay3.homestaybackend.util.RedisLock redisLockUtil;

    private final SpelExpressionParser spelExpressionParser = new SpelExpressionParser();

    @Around("@annotation(redisLock)")
    public Object around(ProceedingJoinPoint joinPoint, RedisLock redisLock) throws Throwable {
        String key = resolveKey(joinPoint, redisLock.key());

        String requestId = redisLockUtil.generateRequestId();
        if (!redisLockUtil.tryLock(key, requestId, Duration.ofSeconds(redisLock.timeoutSeconds()))) {
            log.warn("获取分布式锁失败，跳过: key={}", key);
            return null; // 保持原"跳过"语义（原代码 tryLock 失败直接 return）
        }

        try {
            return joinPoint.proceed();
        } finally {
            redisLockUtil.unlock(key, requestId);
        }
    }

    /**
     * 解析锁 key：纯文本（不含 #）直接返回原文；含 # 视为 SpEL 表达式解析
     * （绑定方法参数，如 handlePaymentNotify 的 #result.outTradeNo）。
     * 锁 key 在执行业务前解析，因此不绑定返回值。
     */
    private String resolveKey(ProceedingJoinPoint joinPoint, String keyExpression) {
        // 纯文本 key（无 SpEL 变量引用），直接使用，不做表达式解析
        if (!keyExpression.contains("#")) {
            return keyExpression;
        }
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Object[] args = joinPoint.getArgs();

        StandardEvaluationContext context = new StandardEvaluationContext();
        java.lang.reflect.Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length && i < args.length; i++) {
            String name = parameters[i].getName();
            if (name != null && !name.isBlank()) {
                context.setVariable(name, args[i]);
            }
        }

        try {
            Object value = spelExpressionParser.parseExpression(keyExpression).getValue(context);
            if (value == null) {
                throw new IllegalArgumentException("分布式锁 key 解析为空: " + keyExpression);
            }
            return String.valueOf(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("分布式锁 key SpEL 解析失败: " + keyExpression, e);
        }
    }
}
