package com.homestay3.homestaybackend.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 接口耗时切面：统计 controller 包下所有方法的耗时。
 * 超过 800ms 记 warn，否则记 debug；不改变任何行为，异常原样抛出。
 */
@Aspect
@Component
@Slf4j
public class ApiTimingAspect {

    /** 慢接口阈值（毫秒） */
    private static final long SLOW_THRESHOLD_MS = 800;

    @Around("execution(* com.homestay3.homestaybackend.controller..*(..))")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.nanoTime();
        try {
            return joinPoint.proceed();
        } finally {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            String methodName = joinPoint.getSignature().getDeclaringTypeName() + "." + joinPoint.getSignature().getName();
            if (elapsedMs > SLOW_THRESHOLD_MS) {
                log.warn("接口耗时 {} ms: {}", elapsedMs, methodName);
            } else {
                log.debug("接口耗时 {} ms: {}", elapsedMs, methodName);
            }
        }
    }
}
