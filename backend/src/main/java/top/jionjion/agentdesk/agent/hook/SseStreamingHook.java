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
import top.jionjion.agentdesk.dto.chat.TaskProgressDto;

import java.io.IOException;
import java.util.*;

/**
 * Hook-SSE bridge: listens to AgentScope Hook events and pushes to SseEmitter
 *
 * @author Jion
 */
public class SseStreamingHook implements Hook {

    private static final Logger log = LoggerFactory.getLogger(SseStreamingHook.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int MAX_TOOL_RETRIES = 3;

    /**
     * PlanNotebook 工具名集合，用于拦截计划事件并发射 task_progress SSE 事件
     */
    private static final Set<String> PLAN_TOOL_NAMES = Set.of(
            "create_plan", "revise_current_plan", "update_subtask_state",
            "finish_subtask", "finish_plan"
    );

    private volatile SseEmitter emitter;

    /**
     * 追踪同一工具连续失败次数，防止死循环重试
     */
    private final Map<String, Integer> toolFailureCount = new HashMap<>();

    /**
     * 会话级计划快照，用于计算任务进度
     */
    private volatile PlanSnapshot planSnapshot;

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
                    log.info("[Hook] PostActingEvent: tool={}, isPlanTool={}", toolName, PLAN_TOOL_NAMES.contains(toolName));
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

                    // PlanNotebook 工具拦截: 解析计划事件并发射 task_progress SSE 事件
                    if (PLAN_TOOL_NAMES.contains(toolName) && toolUse != null) {
                        emitPlanProgressEvent(toolName, toolUse.getInput(), resultText);
                    }

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

                    // 达到最大迭代次数时，追加提示信息告知用户
                    if ("MAX_ITERATIONS".equals(reason)) {
                        String hint = "\n\n⚠️ 已达到最大执行轮次限制，任务被中断。如需继续，请再次发送请求。";
                        content = (content != null ? content : "") + hint;
                        lastReply = content;
                        log.warn("Agent 达到最大迭代次数限制, 会话已中断");
                    }

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

    // ==================== PlanNotebook 事件拦截 ====================

    /**
     * 拦截 PlanNotebook 工具调用，解析参数并发射 task_progress SSE 事件
     */
    @SuppressWarnings("unchecked")
    private void emitPlanProgressEvent(String toolName, Map<String, Object> args, String resultText) {
        try {
            TaskProgressDto progress = switch (toolName) {
                case "create_plan" -> handleCreatePlan(args);
                case "revise_current_plan" -> handleRevisePlan(args);
                case "update_subtask_state" -> handleUpdateSubtaskState(args);
                case "finish_subtask" -> handleFinishSubtask(args);
                case "finish_plan" -> handleFinishPlan(args);
                default -> null;
            };
            if (progress != null) {
                sendEvent("task_progress", ChatEventDto.taskProgress(progress));
            }
        } catch (Exception ex) {
            log.debug("解析 PlanNotebook 事件失败: tool={}, error={}", toolName, ex.getMessage());
        }
    }

    /**
     * 处理 create_plan: 提取计划名称和子任务列表，初始化 PlanSnapshot
     */
    @SuppressWarnings("unchecked")
    private TaskProgressDto handleCreatePlan(Map<String, Object> args) {
        if (args == null) return null;

        String planTitle = getStringOrDefault(args, "name", "未命名计划");
        List<Map<String, Object>> subtaskMaps = (List<Map<String, Object>>) args.get("subtasks");

        List<PlanSnapshot.SubtaskEntry> entries = new ArrayList<>();
        List<TaskProgressDto.SubtaskDto> dtos = new ArrayList<>();
        if (subtaskMaps != null) {
            for (int i = 0; i < subtaskMaps.size(); i++) {
                Map<String, Object> st = subtaskMaps.get(i);
                String name = getStringOrDefault(st, "name", "子任务 " + (i + 1));
                entries.add(new PlanSnapshot.SubtaskEntry(i, name, "todo"));
                dtos.add(new TaskProgressDto.SubtaskDto(String.valueOf(i), name, "todo"));
            }
        }

        planSnapshot = new PlanSnapshot(planTitle, entries);

        return new TaskProgressDto("plan_created", planTitle, dtos,
                null, null, null, 0, dtos.size());
    }

    /**
     * 处理 revise_current_plan: 子任务增删改后重新发送完整快照
     */
    @SuppressWarnings("unchecked")
    private TaskProgressDto handleRevisePlan(Map<String, Object> args) {
        if (planSnapshot == null || args == null) return null;

        String action = getStringOrDefault(args, "action", "");
        int idx = getIntOrDefault(args, "subtask_idx", -1);

        switch (action) {
            case "add" -> {
                Map<String, Object> subtask = (Map<String, Object>) args.get("subtask");
                String name = subtask != null ? getStringOrDefault(subtask, "name", "新子任务") : "新子任务";
                planSnapshot.addSubtask(idx, name);
            }
            case "delete" -> planSnapshot.deleteSubtask(idx);
            case "revise" -> {
                Map<String, Object> subtask = (Map<String, Object>) args.get("subtask");
                if (subtask != null && idx >= 0 && idx < planSnapshot.subtasks.size()) {
                    String name = getStringOrDefault(subtask, "name", planSnapshot.subtasks.get(idx).name);
                    planSnapshot.subtasks.set(idx, new PlanSnapshot.SubtaskEntry(idx, name, planSnapshot.subtasks.get(idx).state));
                }
            }
        }

        // 重建索引
        planSnapshot.reindex();

        return new TaskProgressDto("plan_revised", planSnapshot.planTitle, planSnapshot.toSubtaskDtos(),
                null, null, null, planSnapshot.completedCount(), planSnapshot.totalCount());
    }

    /**
     * 处理 update_subtask_state: 更新单个子任务状态（todo/in_progress/abandoned）
     */
    private TaskProgressDto handleUpdateSubtaskState(Map<String, Object> args) {
        if (planSnapshot == null || args == null) return null;

        int idx = getIntOrDefault(args, "subtask_idx", -1);
        String state = getStringOrDefault(args, "state", "");

        if (idx < 0 || idx >= planSnapshot.subtasks.size()) return null;

        planSnapshot.updateState(idx, state);
        PlanSnapshot.SubtaskEntry entry = planSnapshot.subtasks.get(idx);

        return new TaskProgressDto("task_updated", null, null,
                String.valueOf(idx), entry.name, state,
                planSnapshot.completedCount(), planSnapshot.totalCount());
    }

    /**
     * 处理 finish_subtask: 标记子任务完成
     */
    private TaskProgressDto handleFinishSubtask(Map<String, Object> args) {
        if (planSnapshot == null || args == null) return null;

        int idx = getIntOrDefault(args, "subtask_idx", -1);
        if (idx < 0 || idx >= planSnapshot.subtasks.size()) return null;

        planSnapshot.updateState(idx, "done");
        PlanSnapshot.SubtaskEntry entry = planSnapshot.subtasks.get(idx);

        return new TaskProgressDto("task_completed", null, null,
                String.valueOf(idx), entry.name, "done",
                planSnapshot.completedCount(), planSnapshot.totalCount());
    }

    /**
     * 处理 finish_plan: 整个计划完成或放弃
     */
    private TaskProgressDto handleFinishPlan(Map<String, Object> args) {
        if (planSnapshot == null) return null;

        String state = args != null ? getStringOrDefault(args, "state", "done") : "done";

        return new TaskProgressDto("plan_finished", planSnapshot.planTitle, null,
                null, null, state,
                planSnapshot.completedCount(), planSnapshot.totalCount());
    }

    // ==================== 工具方法 ====================

    private static String getStringOrDefault(Map<String, Object> map, String key, String defaultValue) {
        Object val = map.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    private static int getIntOrDefault(Map<String, Object> map, String key, int defaultValue) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
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

    // ==================== 计划快照内部类 ====================

    /**
     * 会话级计划快照，维护子任务状态列表用于计算进度
     */
    static class PlanSnapshot {
        String planTitle;
        final List<SubtaskEntry> subtasks;

        PlanSnapshot(String planTitle, List<SubtaskEntry> subtasks) {
            this.planTitle = planTitle;
            this.subtasks = new ArrayList<>(subtasks);
        }

        int completedCount() {
            return (int) subtasks.stream().filter(s -> "done".equals(s.state)).count();
        }

        int totalCount() {
            return subtasks.size();
        }

        void updateState(int idx, String newState) {
            if (idx >= 0 && idx < subtasks.size()) {
                SubtaskEntry old = subtasks.get(idx);
                subtasks.set(idx, new SubtaskEntry(idx, old.name, newState));
            }
        }

        void addSubtask(int idx, String name) {
            int insertIdx = (idx >= 0 && idx <= subtasks.size()) ? idx : subtasks.size();
            subtasks.add(insertIdx, new SubtaskEntry(insertIdx, name, "todo"));
        }

        void deleteSubtask(int idx) {
            if (idx >= 0 && idx < subtasks.size()) {
                subtasks.remove(idx);
            }
        }

        /**
         * 重建子任务索引（增删后调用）
         */
        void reindex() {
            for (int i = 0; i < subtasks.size(); i++) {
                SubtaskEntry old = subtasks.get(i);
                if (old.index != i) {
                    subtasks.set(i, new SubtaskEntry(i, old.name, old.state));
                }
            }
        }

        List<TaskProgressDto.SubtaskDto> toSubtaskDtos() {
            return subtasks.stream()
                    .map(e -> new TaskProgressDto.SubtaskDto(String.valueOf(e.index), e.name, e.state))
                    .toList();
        }

        record SubtaskEntry(int index, String name, String state) {
        }
    }
}
