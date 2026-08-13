package com.homestay3.homestaybackend.exception;

import com.homestay3.homestaybackend.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Environment environment;

    /**
     * 生产环境判定：只有显式激活 prod profile 才隐藏内部错误详情。
     * 注意不能用 System.getProperty("spring.profiles.active")——Spring 启动时
     * 不会设置该 JVM 系统属性，恒为 null，导致生产环境误判为 dev 而泄露信息。
     */
    private boolean isProd() {
        return Arrays.asList(environment.getActiveProfiles()).contains("prod");
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleResourceNotFoundException(ResourceNotFoundException ex) {
        ApiResponse<?> response = ApiResponse.error(404, ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<?>> handleRateLimitExceeded(RateLimitExceededException ex) {
        ApiResponse<?> response = ApiResponse.error(429, ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
    }

    @ExceptionHandler(PriceChangedException.class)
    public ResponseEntity<ApiResponse<?>> handlePriceChangedException(PriceChangedException ex) {
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("error", "PRICE_CHANGED");
        data.put("message", ex.getMessage());
        data.put("latestQuote", ex.getLatestQuote());
        ApiResponse<?> response = ApiResponse.error(409, ex.getMessage(), data);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<?>> handleBadCredentialsException(BadCredentialsException ex) {
        ApiResponse<?> response = ApiResponse.error(401, "用户名或密码错误");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResponse<?>> handleSecurityException(SecurityException ex) {
        ApiResponse<?> response = ApiResponse.error(403, ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(LoginException.class)
    public ResponseEntity<ApiResponse<?>> handleLoginException(LoginException ex) {
        ApiResponse<?> response = ApiResponse.error(401, ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        ApiResponse<Map<String, String>> response = ApiResponse.error(400, "参数校验失败", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<?>> handleRuntimeException(RuntimeException ex, WebRequest request) {
        String path = request.getDescription(false).replace("uri=", "");
        logger.error("系统发生运行时异常，路径: {}, 异常: {}", path, ex.getMessage(), ex);
        
        Map<String, Object> data = new HashMap<>();
        
        // 为房东房源列表API提供特定的错误处理（向后兼容）
        if (path.contains("/api/homestays/owner")) {
            data.put("list", new ArrayList<>());
            data.put("total", 0);
            data.put("page", 0);
            data.put("size", 10);
            data.put("pages", 0);
        } else if (path.contains("/api/homestays") && !path.contains("/api/homestays/")) {
            data.put("list", new ArrayList<>());
        }
        
        // 开发环境下返回详细错误信息，生产环境隐藏
        if (!isProd()) {
            data.put("error", ex.getMessage());
            data.put("errorType", ex.getClass().getSimpleName());
        }
        
        ApiResponse<?> response = data.isEmpty() 
            ? ApiResponse.error(500, "系统处理失败，请重试")
            : ApiResponse.error(500, "系统处理失败，请重试", data);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
    
    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<ApiResponse<?>> handleNullPointerException(NullPointerException ex, WebRequest request) {
        logger.error("系统发生空指针异常: {}", ex.getMessage(), ex);
        
        String path = request.getDescription(false);
        Map<String, Object> data = new HashMap<>();
        
        if (path.contains("/api/homestays") && !path.contains("/api/homestays/")) {
            data.put("list", new ArrayList<>());
        }
        
        ApiResponse<?> response = data.isEmpty()
            ? ApiResponse.error(500, "数据处理异常，请重试")
            : ApiResponse.error(500, "数据处理异常，请重试", data);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
    
    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ResponseEntity<ApiResponse<?>> handleDataAccessException(
            org.springframework.dao.DataAccessException ex, WebRequest request) {
        logger.error("数据库访问异常: {}", ex.getMessage(), ex);
        
        String path = request.getDescription(false);
        Map<String, Object> data = new HashMap<>();
        
        if (path.contains("/api/homestays") && !path.contains("/api/homestays/")) {
            data.put("list", new ArrayList<>());
        }
        
        ApiResponse<?> response = data.isEmpty()
            ? ApiResponse.error(503, "数据服务暂时不可用")
            : ApiResponse.error(503, "数据服务暂时不可用", data);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<?>> handleOptimisticLockingFailureException(
            org.springframework.dao.OptimisticLockingFailureException ex) {
        logger.warn("订单乐观锁冲突: {}", ex.getMessage());
        ApiResponse<?> response = ApiResponse.error(409, "订单状态已被其他操作更新，请刷新后重试");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleGlobalException(Exception ex, WebRequest request) {
        logger.error("系统发生未捕获异常: {}", ex.getMessage(), ex);
        
        String path = request.getDescription(false);
        Map<String, Object> data = new HashMap<>();
        
        // 为获取房源列表的API提供空数据而不是错误（向后兼容）
        if (path.contains("/api/homestays") && !path.contains("/api/homestays/")) {
            data.put("list", new ArrayList<>());
        }
        
        // 开发环境下返回详细错误信息，生产环境下隐藏
        if (!isProd()) {
            data.put("error", ex.getMessage());
            data.put("type", ex.getClass().getSimpleName());
        }
        
        String message = path.contains("/api/homestays") && !path.contains("/api/homestays/")
            ? "暂无房源数据"
            : "系统暂时繁忙，请稍后重试";
        
        ApiResponse<?> response = data.isEmpty()
            ? ApiResponse.error(500, message)
            : ApiResponse.error(500, message, data);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
} 
