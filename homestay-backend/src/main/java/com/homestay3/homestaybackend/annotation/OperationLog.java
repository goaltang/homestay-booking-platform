package com.homestay3.homestaybackend.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解：标注在管理员写操作方法上，由 OperationLogAspect 统一记录操作审计日志。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {

    /** 操作类型：CREATE / UPDATE / DELETE */
    String operationType();

    /** 模块名，如 BANNER、ANNOUNCEMENT */
    String resource();

    /** 操作详情，支持 SpEL（如 "创建公告: #{#announcement.title}"），解析失败时回退为原始字符串 */
    String detail() default "";

    /** 资源标识，支持 SpEL（如 #{#id}）；为空时默认取 #result?.id */
    String resourceId() default "";

    /** 操作状态 */
    String status() default "SUCCESS";
}
