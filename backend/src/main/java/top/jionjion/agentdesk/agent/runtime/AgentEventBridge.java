package top.jionjion.agentdesk.agent.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.AgentStartEvent;
import io.agentscope.core.event.AllToolsDeniedEvent;
import io.agentscope.core.event.ExceedMaxItersEvent;
import io.agentscope.core.event.HintBlockEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.event.ModelCallStartEvent;
import io.agentscope.core.event.RequestStopEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolCallDeltaEvent;
import io.agentscope.core.event.ToolCallEndEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultDataDeltaEvent;
import io.agentscope.core.event.ToolResultStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.message.Msg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import top.jionjion.agentdesk.dto.chat.ChatEventDto;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

/** Maps AgentScope v2 typed events to AgentDesk's stable SSE contract. */
public final class AgentEventBridge {

    private static final Logger log = LoggerFactory.getLogger(AgentEventBridge.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final Map<String, StringBuilder> toolArguments = new ConcurrentHashMap<>();
    private final Map<String, StringBuilder> toolResults = new ConcurrentHashMap<>();
    private final Map<String, String> toolNames = new ConcurrentHashMap<>();
    private final Set<String> announcedToolCalls = ConcurrentHashMap.newKeySet();

    private volatile SseEmitter emitter;
    private volatile boolean clientDisconnected;
    private volatile String lastReply;
    private volatile boolean exceededMaxIters;
    private volatile boolean modelTurnHasToolCalls;
    private volatile String stopReason;

    public AgentEventBridge(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void attach(SseEmitter emitter) {
        this.emitter = emitter;
        this.clientDisconnected = false;
        this.lastReply = null;
        this.exceededMaxIters = false;
        this.toolArguments.clear();
        this.toolResults.clear();
        this.toolNames.clear();
        this.announcedToolCalls.clear();
        this.modelTurnHasToolCalls = false;
        this.stopReason = null;
    }

    public void detach() {
        this.emitter = null;
    }

    public void markDisconnected() {
        this.clientDisconnected = true;
        this.emitter = null;
    }

    public boolean isClientDisconnected() {
        return clientDisconnected;
    }

    public String getLastReply() {
        return lastReply;
    }

    public void accept(AgentEvent event) {
        switch (event) {
            case AgentStartEvent ignored -> send("agent_start", ChatEventDto.agentStart());
            case TextBlockDeltaEvent e -> send("text_chunk", ChatEventDto.textChunk(e.getDelta()));
            case ThinkingBlockDeltaEvent e ->
                    send("thinking_chunk", ChatEventDto.thinkingChunk(e.getDelta()));
            case ToolCallStartEvent e -> {
                toolArguments.put(e.getToolCallId(), new StringBuilder());
                toolNames.put(e.getToolCallId(), e.getToolCallName());
                modelTurnHasToolCalls = true;
            }
            case ToolCallDeltaEvent e -> toolArguments
                    .computeIfAbsent(e.getToolCallId(), ignored -> new StringBuilder())
                    .append(e.getDelta());
            // TOOL_CALL_END marks the end of the streamed JSON arguments and happens before
            // execution. Announcing here gives the UI complete, parsed arguments.
            case ToolCallEndEvent e -> announceToolCall(
                    e.getToolCallName(), e.getToolCallId());
            case ToolResultStartEvent e -> announceToolCall(
                    e.getToolCallName(), e.getToolCallId());
            case ToolResultTextDeltaEvent e -> toolResults
                    .computeIfAbsent(e.getToolCallId(), ignored -> new StringBuilder())
                    .append(e.getDelta());
            case ToolResultDataDeltaEvent e -> toolResults
                    .computeIfAbsent(e.getToolCallId(), ignored -> new StringBuilder())
                    .append(serializeData(e.getData()));
            case ToolResultEndEvent e -> {
                String result = toolResults.getOrDefault(e.getToolCallId(), new StringBuilder()).toString();
                if (result.isBlank() && e.getState() != null) {
                    result = "[工具执行状态: " + e.getState() + "]";
                }
                send("tool_call_end", ChatEventDto.toolCallEnd(
                        e.getToolCallName(), e.getToolCallId(), result));
                toolArguments.remove(e.getToolCallId());
                toolResults.remove(e.getToolCallId());
                toolNames.remove(e.getToolCallId());
                announcedToolCalls.remove(e.getToolCallId());
            }
            case ModelCallStartEvent ignored -> modelTurnHasToolCalls = false;
            case ModelCallEndEvent ignored -> send("reasoning_complete",
                    ChatEventDto.reasoningComplete(modelTurnHasToolCalls));
            case HintBlockEvent e -> send("thinking_chunk",
                    ChatEventDto.thinkingChunk(e.getHint()));
            case AllToolsDeniedEvent e -> send("thinking_chunk", ChatEventDto.thinkingChunk(
                    "\n⚠️ 本轮有 " + e.getDeniedToolCalls().size() + " 个工具调用被权限策略拒绝。\n"));
            case RequestStopEvent e -> stopReason = e.getReason();
            case ExceedMaxItersEvent ignored -> exceededMaxIters = true;
            case AgentResultEvent e -> complete(e.getResult());
            default -> {
                // Other v2 events are intentionally not part of the current desktop SSE protocol.
            }
        }
    }

    private void complete(Msg result) {
        String content = result != null ? result.getTextContent() : "";
        String reason = result != null && result.getGenerateReason() != null
                ? result.getGenerateReason().name()
                : "MODEL_STOP";
        if (exceededMaxIters) {
            content = (content == null ? "" : content)
                    + "\n\n⚠️ 已达到最大执行轮次限制，任务被中断。如需继续，请再次发送请求。";
            reason = "MAX_ITERATIONS";
        } else if (stopReason != null && !stopReason.isBlank()) {
            content = (content == null ? "" : content) + "\n\n⚠️ 执行已停止：" + stopReason;
            reason = "STOP_REQUESTED";
        }
        lastReply = content;
        send("agent_complete", ChatEventDto.agentComplete(content, reason, null));
    }

    Map<String, Object> parseArguments(String toolCallId) {
        String raw = toolArguments.getOrDefault(toolCallId, new StringBuilder()).toString();
        if (raw.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(raw, MAP_TYPE);
        } catch (IOException ex) {
            return Map.of("raw", raw);
        }
    }

    private void announceToolCall(String toolName, String toolCallId) {
        toolNames.putIfAbsent(toolCallId, toolName);
        if (announcedToolCalls.add(toolCallId)) {
            send("tool_call_start", ChatEventDto.toolCallStart(
                    toolNames.getOrDefault(toolCallId, toolName), toolCallId,
                    parseArguments(toolCallId)));
        }
    }

    private String serializeData(Object data) {
        if (data == null) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(data);
        } catch (IOException ex) {
            return String.valueOf(data);
        }
    }

    private void send(String eventName, ChatEventDto data) {
        if (clientDisconnected || emitter == null) {
            return;
        }
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(objectMapper.writeValueAsString(data)));
        } catch (IOException | IllegalStateException ex) {
            log.debug("SSE client disconnected while sending {}", eventName);
            markDisconnected();
        }
    }
}
