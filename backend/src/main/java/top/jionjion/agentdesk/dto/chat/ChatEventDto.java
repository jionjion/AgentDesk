package top.jionjion.agentdesk.dto.chat;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * SSE 事件载荷 DTO
 *
 * @param type        事件类型, 如 text_chunk, thinking_chunk, tool_call_start 等
 * @param content     文本内容
 * @param toolName    工具名称
 * @param toolId      工具调用ID
 * @param arguments   工具调用参数
 * @param result      工具调用结果
 * @param reason      完成原因
 * @param error       错误信息
 * @param messageId   数据库消息ID, 用于 agent_complete 事件
 * @param memoryCount 记忆召回数量, 用于 memory_recalled 事件
 * @param taskProgress 任务进度载荷, 用于 task_progress 事件
 * @author Jion
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatEventDto(
        /** 事件类型, 如 text_chunk, thinking_chunk, tool_call_start 等 */
        String type,
        /** 文本内容 */
        String content,
        /** 工具名称 */
        String toolName,
        /** 工具调用ID */
        String toolId,
        /** 工具调用参数 */
        Map<String, Object> arguments,
        /** 工具调用结果 */
        String result,
        /** 完成原因 */
        String reason,
        /** 错误信息 */
        String error,
        /** 数据库消息ID, 用于 agent_complete 事件 */
        Long messageId,
        /** 记忆召回数量, 用于 memory_recalled 事件 */
        Integer memoryCount,
        /** 任务进度载荷, 用于 task_progress 事件 */
        TaskProgressDto taskProgress
) {
    /**
     * 文本片段事件
     */
    public static ChatEventDto textChunk(String content) {
        return new ChatEventDto("text_chunk", content, null, null, null, null, null, null, null, null, null);
    }

    /**
     * 思考片段事件
     */
    public static ChatEventDto thinkingChunk(String content) {
        return new ChatEventDto("thinking_chunk", content, null, null, null, null, null, null, null, null, null);
    }

    /**
     * Agent开始事件
     */
    public static ChatEventDto agentStart() {
        return new ChatEventDto("agent_start", null, null, null, null, null, null, null, null, null, null);
    }

    /**
     * 推理完成事件, content标识是否包含工具调用
     */
    public static ChatEventDto reasoningComplete(boolean hasToolCalls) {
        return new ChatEventDto("reasoning_complete", String.valueOf(hasToolCalls), null, null, null, null, null, null, null, null, null);
    }

    /**
     * 工具调用开始事件
     */
    public static ChatEventDto toolCallStart(String toolName, String toolId, Map<String, Object> arguments) {
        return new ChatEventDto("tool_call_start", null, toolName, toolId, arguments, null, null, null, null, null, null);
    }

    /**
     * 工具调用结束事件
     */
    public static ChatEventDto toolCallEnd(String toolName, String toolId, String result) {
        return new ChatEventDto("tool_call_end", null, toolName, toolId, null, result, null, null, null, null, null);
    }

    /**
     * Agent完成事件, 携带数据库消息ID
     */
    public static ChatEventDto agentComplete(String content, String reason, Long messageId) {
        return new ChatEventDto("agent_complete", content, null, null, null, null, reason, null, messageId, null, null);
    }

    /**
     * 标题生成事件
     */
    public static ChatEventDto titleGenerated(String title) {
        return new ChatEventDto("title_generated", title, null, null, null, null, null, null, null, null, null);
    }

    /**
     * 错误事件
     */
    public static ChatEventDto error(String message) {
        return new ChatEventDto("error", null, null, null, null, null, null, message, null, null, null);
    }

    /**
     * 记忆召回事件
     */
    public static ChatEventDto memoryRecalled(int count) {
        return new ChatEventDto("memory_recalled", null, null, null, null, null, null, null, null, count, null);
    }

    /**
     * 任务进度事件（Harness Task List 执行追踪）
     */
    public static ChatEventDto taskProgress(TaskProgressDto progress) {
        return new ChatEventDto("task_progress", null, null, null, null, null, null, null, null, null, progress);
    }
}
