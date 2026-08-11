package com.homestay3.homestaybackend.aspect;

import com.homestay3.homestaybackend.annotation.OperationLog;
import com.homestay3.homestaybackend.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;

/**
 * 操作日志切面：拦截标注了 @OperationLog 的方法，在方法正常返回后异步记录操作审计日志。
 * 约定：只记录成功，方法抛异常时不记录；日志解析/写入任何异常都不影响业务。
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class OperationLogAspect {

    private final OperationLogService operationLogService;

    private final SpelExpressionParser spelExpressionParser = new SpelExpressionParser();

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        // 先执行业务方法：只记录成功（抛异常则不记录，保持原有语义）
        Object result = joinPoint.proceed();
        try {
            logOperation(joinPoint, operationLog, result);
        } catch (Exception e) {
            // 日志解析/写入失败绝不影响业务
            log.error("记录操作日志异常: resource={}, operationType={}, error={}",
                    operationLog.resource(), operationLog.operationType(), e.getMessage(), e);
        }
        return result;
    }

    private void logOperation(ProceedingJoinPoint joinPoint, OperationLog operationLog, Object result) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Object[] args = joinPoint.getArgs();

        StandardEvaluationContext context = buildContext(method, args, result);

        String detail = operationLog.detail().isEmpty()
                ? ""
                : resolveDetail(context, operationLog.detail());
        String resourceId = resolveResourceId(context, operationLog);

        operationLogService.log(
                resolveOperator(),
                operationLog.operationType(),
                operationLog.resource(),
                resourceId,
                resolveIp(),
                detail,
                operationLog.status());
    }

    /**
     * 构建 SpEL 上下文：绑定方法参数（参数名 -> 参数值）+ #result（返回值）。
     */
    private StandardEvaluationContext buildContext(Method method, Object[] args, Object result) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        java.lang.reflect.Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length && i < args.length; i++) {
            String name = parameters[i].getName();
            if (name != null && !name.isBlank()) {
                context.setVariable(name, args[i]);
            }
        }
        context.setVariable("result", result);
        return context;
    }

    /**
     * 解析 detail：纯文本（不含 #）直接返回原文；含 # 视为 SpEL 表达式解析；
     * 解析失败时回退为注解原始字符串，绝不抛出异常。
     */
    private String resolveDetail(StandardEvaluationContext context, String expression) {
        if (!expression.contains("#")) {
            return expression;
        }
        try {
            Expression expr = spelExpressionParser.parseExpression(expression);
            Object value = expr.getValue(context);
            return value == null ? expression : String.valueOf(value);
        } catch (Exception e) {
            return expression;
        }
    }

    /**
     * 解析 resourceId：优先注解里的 SpEL；为空时默认取 #result?.id（返回值的 id），
     * 返回值为 null 或没有 id 则记录 null。
     */
    private String resolveResourceId(StandardEvaluationContext context, OperationLog operationLog) {
        String expression = operationLog.resourceId().isEmpty() ? "#result?.id" : operationLog.resourceId();
        if (!expression.contains("#")) {
            return expression;
        }
        try {
            Object value = spelExpressionParser.parseExpression(expression).getValue(context);
            return value == null ? null : String.valueOf(value);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取操作人：优先取 SecurityContext 中的用户名；为空/匿名则记 SYSTEM。
     */
    private String resolveOperator() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {
            return "SYSTEM";
        }
        return authentication.getName();
    }

    /**
     * 获取请求 IP：无请求上下文（如定时任务）时返回 null。
     */
    private String resolveIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return request == null ? null : request.getRemoteAddr();
        }
        return null;
    }
}
