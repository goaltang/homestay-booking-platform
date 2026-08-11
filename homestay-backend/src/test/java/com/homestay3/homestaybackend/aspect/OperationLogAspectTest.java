package com.homestay3.homestaybackend.aspect;

import com.homestay3.homestaybackend.annotation.OperationLog;
import com.homestay3.homestaybackend.service.OperationLogService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OperationLogAspect 单元测试（纯 Mockito，不依赖 Spring 上下文/数据库）。
 * 覆盖：成功记录 / 异常不记录 / 无认证用 SYSTEM / 有认证用真实用户名 / 日志异常不影响业务。
 */
@ExtendWith(MockitoExtension.class)
class OperationLogAspectTest {

    @Mock
    private OperationLogService operationLogService;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    private OperationLogAspect aspect;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /** 测试用注解方法：detail 引用参数 #name */
    @OperationLog(operationType = "CREATE", resource = "TEST", detail = "'创建: ' + #name")
    public void annotatedMethod(String name) {
    }

    private OperationLog annotationOf(String methodName) throws NoSuchMethodException {
        Method method = OperationLogAspectTest.class.getMethod(methodName, String.class);
        return method.getAnnotation(OperationLog.class);
    }

    @Test
    void 正常返回时记录操作日志_无认证用户记SYSTEM() throws Throwable {
        aspect = new OperationLogAspect(operationLogService);
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(
                OperationLogAspectTest.class.getMethod("annotatedMethod", String.class));
        when(joinPoint.getArgs()).thenReturn(new Object[]{"海景房"});
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.around(joinPoint, annotationOf("annotatedMethod"));

        assertEquals("ok", result);
        verify(operationLogService).log(eq("SYSTEM"), eq("CREATE"), eq("TEST"),
                any(), any(), eq("创建: 海景房"), eq("SUCCESS"));
    }

    @Test
    void 方法抛异常时不记录日志() throws Throwable {
        aspect = new OperationLogAspect(operationLogService);
        // proceed 抛异常时，around 直接异常传播，后续 signature/args 均不会被读取
        when(joinPoint.proceed()).thenThrow(new RuntimeException("业务失败"));

        assertThrows(RuntimeException.class, () -> aspect.around(joinPoint, annotationOf("annotatedMethod")));
        verify(operationLogService, never()).log(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void 有认证用户时记录真实用户名() throws Throwable {
        aspect = new OperationLogAspect(operationLogService);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null));
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(
                OperationLogAspectTest.class.getMethod("annotatedMethod", String.class));
        when(joinPoint.getArgs()).thenReturn(new Object[]{"海景房"});
        when(joinPoint.proceed()).thenReturn("ok");

        aspect.around(joinPoint, annotationOf("annotatedMethod"));

        verify(operationLogService).log(eq("admin"), eq("CREATE"), eq("TEST"),
                any(), any(), eq("创建: 海景房"), eq("SUCCESS"));
    }

    @Test
    void 日志服务抛异常时不影响业务返回() throws Throwable {
        aspect = new OperationLogAspect(operationLogService);
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(
                OperationLogAspectTest.class.getMethod("annotatedMethod", String.class));
        when(joinPoint.getArgs()).thenReturn(new Object[]{"海景房"});
        when(joinPoint.proceed()).thenReturn("ok");
        doThrow(new RuntimeException("日志写入失败")).when(operationLogService)
                .log(any(), any(), any(), any(), any(), any(), any());

        Object result = aspect.around(joinPoint, annotationOf("annotatedMethod"));

        assertEquals("ok", result);
    }
}
