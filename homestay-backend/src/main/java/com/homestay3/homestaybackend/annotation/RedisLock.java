package com.homestay3.homestaybackend.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 分布式锁注解：标注在需要加锁保护的方法上，由 RedisLockAspect 统一处理加锁/解锁。
 * key 支持 SpEL（绑定方法参数），如 "payment:notify:#{#result.outTradeNo}"。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RedisLock {

    /** 锁的业务键，支持 SpEL（绑定方法参数） */
    String key();

    /** 锁的过期时间（秒），防止死锁 */
    long timeoutSeconds() default 30;
}
