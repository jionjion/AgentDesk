package top.jionjion.agentdesk.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PreCallEvent;
import io.agentscope.core.hook.ReasoningChunkEvent;
import io.agentscope.core.hook.PostReasoningEvent;
import io.agentscope.core.hook.PreActingEvent;
import io.agentscope.core.hook.PostActingEvent;
import io.agentscope.core.hook.PostCallEvent;
import io.agentscope.core.hook.ErrorEvent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Mono;
import top.jionjion.agentdesk.dto.ChatEventDto;

import java.util.HashMap;
import java.util.Map;

/**
 * Hook-SSE bridge: listens to AgentScope Hook events and pushes to SseEmitter
 */
public class SseStreamingHook implements Hook {

    private static final Logger log = LoggerFactory.getLogger(SseStreamingHook.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private volatile SseEmitter emitter;
    private volatile String lastReply;

    public void setEmitter(SseEmitter emitter) {
        this.emitter = emitter;
        this.lastReply = null;
    }

    /**
     * 获取最近一次 Agent 回复的完整文本
     */
    public String getLastReply() {
        return lastReply;
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        try {
            switch (event) {
                case PreCallEvent e -> sendEvent("agent_start", ChatEventDto.agentStart());

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
                    sendEvent("tool_call_end", ChatEventDto.toolCallEnd(toolName, toolId, resultText));
                }

                case PostCallEvent e -> {
                    Msg finalMsg = e.getFinalMessage();
                    String content = finalMsg != null ? finalMsg.getTextContent() : "";
                    String reason = finalMsg != null && finalMsg.getGenerateReason() != null
                            ? finalMsg.getGenerateReason().name() : "MODEL_STOP";
                    lastReply = content;
                    sendEvent("agent_complete", ChatEventDto.agentComplete(content, reason));
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

    @Override
    public int priority() {
        return 100;
    }

    private void sendEvent(String eventName, ChatEventDto data) {
        SseEmitter currentEmitter = this.emitter;
        if (currentEmitter == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(data);
            currentEmitter.send(SseEmitter.event().name(eventName).data(json));
        } catch (Exception e) {
            log.debug("Failed to send SSE event {}: {}", eventName, e.getMessage());
        }
    }
}
