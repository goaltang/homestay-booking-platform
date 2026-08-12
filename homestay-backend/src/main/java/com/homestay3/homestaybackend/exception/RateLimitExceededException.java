package com.homestay3.homestaybackend.exception;

/**
 * 限流异常：请求超过接口限流阈值时抛出，由全局异常处理器转为 HTTP 429。
 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
