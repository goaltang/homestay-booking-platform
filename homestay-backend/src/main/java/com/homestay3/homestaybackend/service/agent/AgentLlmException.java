package com.homestay3.homestaybackend.service.agent;

/**
 * Agent 调用 LLM 失败时抛出的异常（超时、HTTP 错误、响应解析失败等）
 */
public class AgentLlmException extends RuntimeException {

    public AgentLlmException(String message) {
        super(message);
    }

    public AgentLlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
