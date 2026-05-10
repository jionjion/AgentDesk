package top.jionjion.agentdesk.agent.hook;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.hook.*;
import io.agentscope.core.message.*;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Mono;
import top.jionjion.agentdesk.dto.chat.ChatEventDto;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Hook-SSE bridge: listens to AgentScope Hook events and pushes to SseEmitter
 *
 * @author Jion
 */
public class SseStreamingHook implements Hook {

    private static final Logger log = LoggerFactory.getLogger(SseStreamingHook.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int MAX_TOOL_RETRIES = 3;

    private volatile SseEmitter emitter;

    /**
     * 追踪同一工具连续失败次数，防止死循环重试
     */
    private final Map<String, Integer> toolFailureCount = new HashMap<>();

    /**
     * 获取最近一次 Agent 回复的完整文本
     */
    @Getter
    private volatile String lastReply;

    /**
     * 标记客户端是否已断开, 避免重复写入已关闭的连接
     */
    @Getter
    private volatile boolean clientDisconnected;

    /**
     * 是否启用了长期记忆 (由外部设置)
     * 设置是否启用了长期记忆
     */
    @Setter
    private volatile boolean longTermMemoryEnabled;

    public void setEmitter(SseEmitter emitter) {
        this.emitter = emitter;
        this.lastReply = null;
        this.clientDisconnected = false;
    }

    /**
     * 标记客户端已断开连接
     */
    public void markDisconnected() {
        this.clientDisconnected = true;
        this.emitter = null;
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        try {
            switch (event) {
                case PreCallEvent ignored -> {
                    toolFailureCount.clear();
                    sendEvent("agent_start", ChatEventDto.agentStart());
                }

                case ReasoningChunkEvent e -> {
                    Msg chunk = e.getIncrementalChunk();
                    if (chunk != null && chunk.getContent() != null) {
                        for (ContentBlock block : chunk.getContent()) {
                            if (block instanceof ThinkingBlock tb) {
                                sendEvent("thinking_chunk", ChatEventDto.thinkingChunk(tb.getThinking()));
                            } else if (block instanceof TextBlock tb) {
                                sendEvent("text_chunk", ChatEventDto.textChunk(tb.getText()));
                            }
                        }
                    }
                }

                case PostReasoningEvent e -> {
                    Msg msg = e.getReasoningMessage();
                    boolean hasToolCalls = false;
                    if (msg != null && msg.getContent() != null) {
                        hasToolCalls = msg.getContent().stream()
                                .anyMatch(b -> b instanceof ToolUseBlock);
                    }
                    sendEvent("reasoning_complete", ChatEventDto.reasoningComplete(hasToolCalls));
                }

                case PreActingEvent e -> {
                    ToolUseBlock toolUse = e.getToolUse();
                    if (toolUse != null) {
                        Map<String, Object> args = new HashMap<>();
                        if (toolUse.getInput() != null) {
                            args.putAll(toolUse.getInput());
                        }
                        sendEvent("tool_call_start", ChatEventDto.toolCallStart(
                                toolUse.getName(), toolUse.getId(), args));
                    }
                }

                case PostActingEvent e -> {
                    ToolUseBlock toolUse = e.getToolUse();
                    ToolResultBlock result = e.getToolResult();
                    String toolName = toolUse != null ? toolUse.getName() : "";
                    String toolId = toolUse != null ? toolUse.getId() : "";
                    String resultText = "";
                    if (result != null && result.getOutput() != null) {
                        resultText = result.getOutput().stream()
                                .filter(b -> b instanceof TextBlock)
                                .map(b -> ((TextBlock) b).getText())
                                .reduce("", (a, b) -> a + b);
                    }

                    // 追踪工具连续失败，超过阈值后注入停止重试提示
                    if (resultText.contains("Error:") || resultText.contains("error")) {
                        int count = toolFailureCount.merge(toolName, 1, Integer::sum);
                        if (count >= MAX_TOOL_RETRIES && result != null && result.getOutput() != null) {
                            result.getOutput().add(TextBlock.builder()
                                    .text("\n\n[SYSTEM] 该工具已连续失败 " + count + " 次，请勿再次重试。直接告知用户该工具/服务暂时不可用，并尝试其他方式回答。")
                                    .build());
                            log.warn("工具 {} 已连续失败 {} 次，已注入停止重试提示", toolName, count);
                        }
                    } else {
                        toolFailureCount.remove(toolName);
                    }

                    sendEvent("tool_call_end", ChatEventDto.toolCallEnd(toolName, toolId, resultText));

                    // AGENT_CONTROL 模式: Agent 主动调用 retrieveFromMemory 时通知前端
                    if (longTermMemoryEnabled && "retrieveFromMemory".equals(toolName)
                            && !resultText.contains("No relevant memories found")
                            && !resultText.contains("No keywords provided")) {
                        sendEvent("memory_recalled", ChatEventDto.memoryRecalled(1));
                    }
                }

                case PostCallEvent e -> {
                    Msg finalMsg = e.getFinalMessage();
                    String content = finalMsg != null ? finalMsg.getTextContent() : "";
                    String reason = finalMsg != null && finalMsg.getGenerateReason() != null
                            ? finalMsg.getGenerateReason().name() : "MODEL_STOP";
                    lastReply = content;
                    sendEvent("agent_complete", ChatEventDto.agentComplete(content, reason, null));
                }

                case ErrorEvent e -> {
                    String errorMsg = e.getError() != null ? e.getError().getMessage() : "unknown error";
                    sendEvent("error", ChatEventDto.error(errorMsg));
                }

                default -> {
                    // ignore other event types
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to send SSE event: {}", ex.getMessage());
        }
        return Mono.just(event);
    }

    private void sendEvent(String eventName, ChatEventDto data) {
        if (clientDisconnected) {
            return;
        }
        SseEmitter currentEmitter = this.emitter;
        if (currentEmitter == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(data);
            currentEmitter.send(SseEmitter.event().name(eventName).data(json));
        } catch (IOException e) {
            // 客户端断开连接 (Broken pipe), 标记断开并静默处理
            log.debug("客户端已断开连接, 停止SSE推送, 事件: {}", eventName);
            markDisconnected();
        } catch (IllegalStateException e) {
            // Emitter 已完成或超时
            log.debug("SSE Emitter 已关闭, 事件: {}, 原因: {}", eventName, e.getMessage());
            markDisconnected();
        } catch (Exception e) {
            log.debug("Failed to send SSE event {}: {}", eventName, e.getMessage());
        }
    }
}
