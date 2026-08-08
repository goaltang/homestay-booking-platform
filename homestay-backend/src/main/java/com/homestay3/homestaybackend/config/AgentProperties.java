package com.homestay3.homestaybackend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 客服 Agent 配置
 * 默认关闭（enabled=false），api-key 通过 application-local.properties 注入，不进代码库
 */
@Data
@ConfigurationProperties(prefix = "agent.llm")
public class AgentProperties {

    private boolean enabled = false;

    private String baseUrl = "https://token-plan.cn-beijing.maas.aliyuncs.com/compatible-mode/v1";

    private String apiKey;

    private String model = "deepseek-v4-flash-0731";

    private int timeoutSeconds = 60;

    private int maxTokens = 1024;

    private double temperature = 0.3;

    private int maxToolHops = 2;
}
