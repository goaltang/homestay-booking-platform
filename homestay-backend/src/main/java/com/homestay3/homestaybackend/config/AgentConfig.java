package com.homestay3.homestaybackend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI 客服 Agent 配置注册
 */
@Configuration
@EnableConfigurationProperties(AgentProperties.class)
public class AgentConfig {
}
