package com.homestay3.homestaybackend.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homestay3.homestaybackend.config.AgentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM HTTP 客户端
 * 封装 OpenAI 兼容格式 chat/completions 调用（不依赖原生 function calling）
 */
@Service
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    private final AgentProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public LlmClient(AgentProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeoutMillis = (int) Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())).toMillis();
        factory.setConnectTimeout(timeoutMillis);
        factory.setReadTimeout(timeoutMillis);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 发起一轮 chat 调用，返回模型回复文本
     *
     * @param messages OpenAI 格式消息列表，每条含 role / content
     * @return 模型回复内容（choices[0].message.content）
     * @throws IllegalStateException agent 未启用（enabled=false 或 apiKey 为空）
     * @throws AgentLlmException     HTTP 调用失败或响应解析失败
     */
    public String chat(List<Map<String, String>> messages) {
        if (!properties.isEnabled() || properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("agent 未启用");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("messages", messages);
        body.put("max_tokens", properties.getMaxTokens());
        body.put("temperature", properties.getTemperature());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiKey());

        String url = properties.getBaseUrl() + "/chat/completions";
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        String responseBody;
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            responseBody = response.getBody();
        } catch (RestClientException e) {
            log.error("LLM 调用失败: {}", e.getMessage());
            throw new AgentLlmException("LLM 调用失败: " + e.getMessage(), e);
        }

        if (responseBody == null || responseBody.isBlank()) {
            throw new AgentLlmException("LLM 返回空响应");
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull()) {
                throw new AgentLlmException("LLM 响应缺少 choices[0].message.content");
            }
            return content.asText();
        } catch (AgentLlmException e) {
            throw e;
        } catch (Exception e) {
            throw new AgentLlmException("LLM 响应解析失败: " + e.getMessage(), e);
        }
    }
}
