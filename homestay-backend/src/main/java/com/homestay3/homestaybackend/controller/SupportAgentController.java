package com.homestay3.homestaybackend.controller;

import com.homestay3.homestaybackend.config.AgentProperties;
import com.homestay3.homestaybackend.dto.AgentChatRequest;
import com.homestay3.homestaybackend.dto.AgentChatResponse;
import com.homestay3.homestaybackend.service.agent.SupportAgentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AI 客服 Agent（第一层 FAQ，只读）
 * 走 SecurityConfig 默认的 authenticated 规则，需携带 JWT
 */
@RestController
@RequestMapping("/api/support/agent")
@RequiredArgsConstructor
public class SupportAgentController {

    private final SupportAgentService supportAgentService;
    private final AgentProperties agentProperties;

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@Valid @RequestBody AgentChatRequest request,
                                  Authentication authentication) {
        if (!agentProperties.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "AI客服功能未启用"));
        }
        AgentChatResponse response = supportAgentService.chat(request, authentication.getName());
        return ResponseEntity.ok(response);
    }
}
