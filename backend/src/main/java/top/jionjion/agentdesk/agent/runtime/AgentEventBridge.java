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
import io.agentscope.core.message.ToolResultState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import top.jionjion.agentdesk.dto.chat.ChatEventDto;
import top.jionjion.agentdesk.dto.chat.SubagentEventDto;
import top.jionjion.agentdesk.dto.chat.TaskProgressDto;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

/** Maps AgentScope v2 typed events to AgentDesk's stable SSE contract. */
public final class AgentEventBridge {

    private static final Logger log = LoggerFactory.getLogger(AgentEventBridge.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    /** 子智能体 agentId -> 中文显示名, 用于 subagent_event 的 displayName */
    private final Map<String, String> subagentDisplayNames;
    private final TaskProgressTracker taskProgressTracker = new TaskProgressTracker();
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
        this(objectMapper, Map.of());
    }

    public AgentEventBridge(ObjectMapper objectMapper, Map<String, String> subagentDisplayNames) {
        this.objectMapper = objectMapper;
        this.subagentDisplayNames = Map.copyOf(subagentDisplayNames);
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
        String source = event.getSource();
        if (source != null) {
            handleSubagentEvent(source, event);
            return;
        }
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
                // 被权限拒绝的调用未写入状态, 不推送任务进度
                if (e.getState() != ToolResultState.DENIED) {
                    emitTaskProgress(e.getToolCallName(), e.getToolCallId(), result);
                }
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

    /**
     * 子智能体事件隔离: v2 将子智能体(SubagentDeclaration)内部事件以 source 打标
     * 转发进主 Agent 事件流, 此处将其翻译为独立的 subagent_event, 由前端按 source
     * 分组渲染为可折叠面板。子智能体事件不得触碰主流程状态字段
     * (modelTurnHasToolCalls / stopReason / exceededMaxIters / lastReply)。
     * toolCallId 为全局唯一 UUID, 可安全复用主流程的参数/结果聚合 Map。
     */
    private void handleSubagentEvent(String source, AgentEvent event) {
        String agentId = source.substring(source.lastIndexOf('/') + 1);
        String displayName = subagentDisplayNames.getOrDefault(agentId, agentId);
        switch (event) {
            case AgentStartEvent e -> sendSubagent(new SubagentEventDto(
                    source, agentId, displayName, "start", e.getName(), null, null, null, null));
            case TextBlockDeltaEvent e -> sendSubagent(new SubagentEventDto(
                    source, agentId, displayName, "text_chunk", e.getDelta(), null, null, null, null));
            case ThinkingBlockDeltaEvent e -> sendSubagent(new SubagentEventDto(
                    source, agentId, displayName, "thinking_chunk", e.getDelta(), null, null, null, null));
            case ToolCallStartEvent e -> {
                toolArguments.put(e.getToolCallId(), new StringBuilder());
                toolNames.put(e.getToolCallId(), e.getToolCallName());
            }
            case ToolCallDeltaEvent e -> toolArguments
                    .computeIfAbsent(e.getToolCallId(), ignored -> new StringBuilder())
                    .append(e.getDelta());
            // 参数聚合完毕后再宣告, 与主流程一致
            case ToolCallEndEvent e -> {
                if (announcedToolCalls.add(e.getToolCallId())) {
                    sendSubagent(new SubagentEventDto(
                            source, agentId, displayName, "tool_call_start", null,
                            e.getToolCallName(), e.getToolCallId(),
                            parseArguments(e.getToolCallId()), null));
                }
            }
            case ToolResultTextDeltaEvent e -> toolResults
                    .computeIfAbsent(e.getToolCallId(), ignored -> new StringBuilder())
                    .append(e.getDelta());
            case ToolResultDataDeltaEvent e -> toolResults
                    .computeIfAbsent(e.getToolCallId(), ignored -> new StringBuilder())
                    .append(serializeData(e.getData()));
            case ToolResultEndEvent e -> {
                String result = toolResults.getOrDefault(e.getToolCallId(), new StringBuilder()).toString();
                sendSubagent(new SubagentEventDto(
                        source, agentId, displayName, "tool_call_end", null,
                        e.getToolCallName(), e.getToolCallId(), null, result));
                toolArguments.remove(e.getToolCallId());
                toolResults.remove(e.getToolCallId());
                toolNames.remove(e.getToolCallId());
                announcedToolCalls.remove(e.getToolCallId());
            }
            case AgentResultEvent e -> sendSubagent(new SubagentEventDto(
                    source, agentId, displayName, "complete",
                    e.getResult() != null ? e.getResult().getTextContent() : "",
                    null, null, null, null));
            default -> {
                // 子智能体的模型轮次/提示/停止等事件不进入桌面 SSE 协议
            }
        }
    }

    private void sendSubagent(SubagentEventDto dto) {
        send("subagent_event", ChatEventDto.subagentEvent(dto));
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
        } else {
            // 正常完成时收尾任务进度: 模型常在完成末尾任务后不再调 todo_write,
            // 中断/超轮次路径不补发, 保留真实的未完成状态
            for (TaskProgressDto dto : taskProgressTracker.onAgentComplete()) {
                send("task_progress", ChatEventDto.taskProgress(dto));
            }
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

    /**
     * 拦截 Harness 内置 todo_write 工具结果, 将全量任务列表 diff 为 task_progress 增量事件。
     * v2 框架没有 TaskList 专用事件, 这是任务清单进入 SSE 协议的唯一入口。
     */
    private void emitTaskProgress(String toolName, String toolCallId, String result) {
        if (!"todo_write".equals(toolName)) {
            return;
        }
        // TodoTools 校验失败时返回 "Error: ..." 且不写入状态, 此时不推送
        if (result != null && result.startsWith("Error")) {
            return;
        }
        Object todos = parseArguments(toolCallId).get("todos");
        if (!(todos instanceof List<?> list)) {
            return;
        }
        for (TaskProgressDto dto : taskProgressTracker.onTodoWrite(list)) {
            send("task_progress", ChatEventDto.taskProgress(dto));
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
