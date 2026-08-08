package com.homestay3.homestaybackend.service.agent.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homestay3.homestaybackend.config.AgentProperties;
import com.homestay3.homestaybackend.dto.AgentChatRequest;
import com.homestay3.homestaybackend.dto.AgentChatResponse;
import com.homestay3.homestaybackend.exception.AccessDeniedException;
import com.homestay3.homestaybackend.service.agent.AgentToolRegistry;
import com.homestay3.homestaybackend.service.agent.LlmClient;
import com.homestay3.homestaybackend.service.agent.SupportAgentService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI 客服 Agent（第一层 FAQ，只读）编排实现
 *
 * 两阶段 JSON 协议（不依赖原生 function calling）：
 * 1. 决策轮：LLM 根据问题和工具清单返回严格 JSON {"need_tool":..., "tool":..., "args":{...}}
 * 2. 回答轮：LLM 只能基于工具返回的真实数据回答，禁止编造
 *
 * 护栏：
 * - 敏感词（人身安全/受伤/火灾等）直达人工，不调 LLM
 * - 同一会话 3 轮未解决，第 4 轮起转人工
 * - LLM 调用失败兜底转人工，不抛给前端
 */
@Service
@RequiredArgsConstructor
public class SupportAgentServiceImpl implements SupportAgentService {

    private static final Logger log = LoggerFactory.getLogger(SupportAgentServiceImpl.class);

    private static final int MAX_ROUNDS = 3;
    private static final long CONVERSATION_TTL_MILLIS = Duration.ofHours(1).toMillis();

    private static final List<String> SENSITIVE_KEYWORDS = List.of(
            "人身安全", "受伤", "火灾", "报警", "急救", "救护车", "自杀", "生命危险", "被入侵", "入室盗窃");

    private static final String SENSITIVE_REPLY =
            "您的人身安全最重要！如遇紧急情况，请立即拨打 110（报警）或 120（急救）。"
                    + "我已同时通知人工客服，会尽快与您联系。请优先确保自身安全。";

    private static final String HANDOFF_REPLY =
            "很抱歉没能帮您解决问题，我已为您转接人工客服，请稍等片刻。";

    private static final String FALLBACK_REPLY =
            "客服繁忙，已为您转接人工客服，请稍等片刻。";

    private static final String DECISION_SYSTEM_TEMPLATE =
            "你是民宿平台AI客服的决策模块。根据用户问题和可用工具列表，判断是否需要调用工具获取数据。\n"
                    + "可用工具：\n%s\n"
                    + "只允许回复一个严格JSON对象，不要输出任何其他文字、解释或markdown标记：\n"
                    + "需要工具时：{\"need_tool\": true, \"tool\": \"工具名\", \"args\": {参数对象}}\n"
                    + "不需要工具时：{\"need_tool\": false}\n"
                    + "规则：\n"
                    + "1. 只能从上述列表选择工具，禁止编造工具名\n"
                    + "2. args 只能使用用户问题或上下文中明确给出的信息，禁止编造参数值\n"
                    + "3. 闲聊、投诉、无法用工具回答的问题，一律回复 {\"need_tool\": false}";

    private static final String ANSWER_SYSTEM_PROMPT =
            "你是民宿平台的AI客服。铁律：\n"
                    + "1. 只能基于【工具返回的数据】回答用户问题，禁止编造任何信息\n"
                    + "2. 金额、退款政策、押金状态必须来自工具结果，不得自行估算\n"
                    + "3. 工具数据里没有的信息，明确回复：这个我需要转人工客服为您确认\n"
                    + "4. 回答简洁、友好，使用中文";

    private final LlmClient llmClient;
    private final AgentToolRegistry toolRegistry;
    private final AgentProperties properties;
    private final ObjectMapper objectMapper;

    private final Map<String, ConversationState> conversations = new ConcurrentHashMap<>();

    @Override
    public AgentChatResponse chat(AgentChatRequest request, String username) {
        String conversationId = (request.getConversationId() == null || request.getConversationId().isBlank())
                ? UUID.randomUUID().toString()
                : request.getConversationId();
        String question = request.getQuestion() == null ? "" : request.getQuestion().trim();

        if (containsSensitiveKeyword(question)) {
            log.info("Agent 命中敏感词，直接转人工: conversationId={}", conversationId);
            return handoff(conversationId, null, SENSITIVE_REPLY);
        }

        ConversationState state = touchConversation(conversationId);
        int round = state.rounds.incrementAndGet();
        if (round > MAX_ROUNDS) {
            log.info("Agent 会话 {} 已达 {} 轮未解决，转人工", conversationId, MAX_ROUNDS);
            return handoff(conversationId, null, HANDOFF_REPLY);
        }

        try {
            return doChat(request, username, question, conversationId);
        } catch (Exception e) {
            log.error("Agent 处理失败，兜底转人工: conversationId={}, error={}", conversationId, e.getMessage(), e);
            return handoff(conversationId, null, FALLBACK_REPLY);
        }
    }

    // ==================== 编排主流程 ====================

    private AgentChatResponse doChat(AgentChatRequest request, String username,
                                     String question, String conversationId) {
        List<String> toolTrace = new ArrayList<>();
        String toolUsed = null;
        int hops = 0;

        Decision decision = decide(buildDecisionMessages(request, question, toolTrace));
        while (decision != null && decision.needTool() && hops < properties.getMaxToolHops()) {
            hops++;
            if (toolUsed == null) {
                toolUsed = decision.tool();
            }
            String toolResult = executeToolSafely(decision.tool(), decision.args(), username);
            toolTrace.add("工具 " + decision.tool() + " 返回数据: " + toolResult);
            decision = decide(buildDecisionMessages(request, question, toolTrace));
        }

        String answer = answer(question, toolTrace);
        return AgentChatResponse.builder()
                .conversationId(conversationId)
                .answer(answer)
                .handoffToHuman(false)
                .toolUsed(toolUsed)
                .build();
    }

    /**
     * 决策轮：要求 LLM 返回严格 JSON；解析失败一律降级为"无工具直接回答"
     */
    private Decision decide(List<Map<String, String>> messages) {
        String raw = llmClient.chat(messages);
        String json = extractFirstJsonObject(raw);
        if (json == null) {
            log.warn("Agent 决策回复中未找到JSON块，降级为直接回答");
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            boolean needTool = node.path("need_tool").asBoolean(false);
            if (!needTool) {
                return new Decision(false, null, Map.of());
            }
            String tool = node.path("tool").asText("");
            if (tool.isBlank()) {
                return new Decision(false, null, Map.of());
            }
            Map<String, Object> args = parseArgs(node.path("args"));
            return new Decision(true, tool, args);
        } catch (Exception e) {
            log.warn("Agent 决策JSON解析失败，降级为直接回答: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArgs(JsonNode argsNode) {
        if (argsNode == null || argsNode.isMissingNode() || argsNode.isNull()) {
            return Map.of();
        }
        Map<String, Object> converted = objectMapper.convertValue(argsNode, Map.class);
        return converted == null ? Map.of() : converted;
    }

    /**
     * 回答轮：只能基于工具数据回答
     */
    private String answer(String question, List<String> toolTrace) {
        StringBuilder userContent = new StringBuilder("用户问题: ").append(question);
        if (toolTrace.isEmpty()) {
            userContent.append("\n\n（本次没有工具数据。凡是不确定的信息，必须回复需要转人工客服确认。）");
        } else {
            userContent.append("\n\n【工具返回的数据】\n").append(String.join("\n", toolTrace));
        }
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(message("system", ANSWER_SYSTEM_PROMPT));
        messages.add(message("user", userContent.toString()));
        return llmClient.chat(messages);
    }

    /**
     * 工具执行：任何异常都由上层捕获转成文本交给 LLM，绝不让 agent 直接 500
     */
    private String executeToolSafely(String toolName, Map<String, Object> args, String username) {
        try {
            Object result = toolRegistry.execute(toolName, args, username);
            return objectMapper.writeValueAsString(result);
        } catch (AccessDeniedException e) {
            log.warn("Agent 工具 {} 越权访问被拒绝: username={}", toolName, username);
            return "工具调用失败: 当前用户无权访问该数据";
        } catch (Exception e) {
            log.warn("Agent 工具 {} 调用失败: {}", toolName, e.getMessage());
            return "工具调用失败: " + e.getMessage();
        }
    }

    // ==================== 消息构造 ====================

    private List<Map<String, String>> buildDecisionMessages(AgentChatRequest request,
                                                            String question,
                                                            List<String> toolTrace) {
        String systemPrompt = String.format(DECISION_SYSTEM_TEMPLATE,
                String.join("\n", toolRegistry.toolSpecs()));

        StringBuilder userContent = new StringBuilder("用户问题: ").append(question);
        if (request.getOrderId() != null) {
            userContent.append("\n上下文提示: 用户当前正在查看订单ID ").append(request.getOrderId());
        }
        if (request.getHomestayId() != null) {
            userContent.append("\n上下文提示: 用户当前正在查看房源ID ").append(request.getHomestayId());
        }
        if (!toolTrace.isEmpty()) {
            userContent.append("\n\n【已调用工具的结果】\n").append(String.join("\n", toolTrace))
                    .append("\n\n请判断是否还需要调用其他工具。如不需要，回复 {\"need_tool\": false}");
        }

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(message("system", systemPrompt));
        messages.add(message("user", userContent.toString()));
        return messages;
    }

    private static Map<String, String> message(String role, String content) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    // ==================== 护栏 ====================

    private boolean containsSensitiveKeyword(String question) {
        if (question == null || question.isEmpty()) {
            return false;
        }
        for (String keyword : SENSITIVE_KEYWORDS) {
            if (question.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 会话计数（内存 + TTL 1 小时，惰性清理过期会话）
     */
    private ConversationState touchConversation(String conversationId) {
        long now = System.currentTimeMillis();
        conversations.entrySet().removeIf(e -> now - e.getValue().lastAccessMillis > CONVERSATION_TTL_MILLIS);
        ConversationState state = conversations.computeIfAbsent(conversationId, k -> new ConversationState());
        state.lastAccessMillis = now;
        return state;
    }

    private AgentChatResponse handoff(String conversationId, String toolUsed, String answer) {
        return AgentChatResponse.builder()
                .conversationId(conversationId)
                .answer(answer)
                .handoffToHuman(true)
                .toolUsed(toolUsed)
                .build();
    }

    /**
     * 从文本中提取第一个完整的 {...} JSON 块（容忍 LLM 夹带解释文字）
     */
    static String extractFirstJsonObject(String text) {
        if (text == null) {
            return null;
        }
        int start = text.indexOf('{');
        if (start < 0) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
            } else {
                if (c == '"') {
                    inString = true;
                } else if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return text.substring(start, i + 1);
                    }
                }
            }
        }
        return null;
    }

    // ==================== 内部类型 ====================

    private record Decision(boolean needTool, String tool, Map<String, Object> args) {
    }

    private static final class ConversationState {
        private final AtomicInteger rounds = new AtomicInteger(0);
        private volatile long lastAccessMillis = System.currentTimeMillis();
    }
}
