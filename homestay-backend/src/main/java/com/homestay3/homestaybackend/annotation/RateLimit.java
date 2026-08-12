package com.homestay3.homestaybackend.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解：标注在 Controller 写接口上，由 RateLimitAspect 统一做 Redis 固定窗口限流。
 * 超出限制抛 RateLimitExceededException（全局异常处理返回 429）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 窗口内最大允许次数 */
    int limit();

    /** 窗口时长（秒） */
    int windowSeconds() default 60;

    /** 可选限流 key；为空时默认 = IP + 类名.方法名 */
    String key() default "";
}
