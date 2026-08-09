package com.homestay3.homestaybackend.controller;

import com.homestay3.homestaybackend.config.AgentProperties;
import com.homestay3.homestaybackend.dto.AgentChatRequest;
import com.homestay3.homestaybackend.dto.AgentChatResponse;
import com.homestay3.homestaybackend.dto.AgentPendingAction;
import com.homestay3.homestaybackend.exception.AccessDeniedException;
import com.homestay3.homestaybackend.exception.ResourceNotFoundException;
import com.homestay3.homestaybackend.service.agent.SupportAgentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AI 客服 Agent（第一层 FAQ，只读 + 申请型写操作确认）
 * 走 SecurityConfig 默认的 authenticated 规则，需携带 JWT
 */
@RestController
@RequestMapping("/api/support/agent")
@RequiredArgsConstructor
public class SupportAgentController {

    private static final Logger log = LoggerFactory.getLogger(SupportAgentController.class);

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

    /**
     * 确认执行待确认操作（申请型写操作真正执行点）
     * 前端在展示确认卡片后由用户点击确认调用；服务端校验订单归属后按 action 分发执行
     */
    @PostMapping("/confirm")
    public ResponseEntity<?> confirmAction(@RequestBody AgentPendingAction pending,
                                           Authentication authentication) {
        if (!agentProperties.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "AI客服功能未启用"));
        }
        try {
            AgentChatResponse response = supportAgentService.confirmAction(pending, authentication.getName());
            return ResponseEntity.ok(response);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", e.getMessage()));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", e.getMessage()));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("确认执行操作失败: action={}", pending == null ? null : pending.getAction(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "操作执行失败: " + e.getMessage()));
        }
    }
}
