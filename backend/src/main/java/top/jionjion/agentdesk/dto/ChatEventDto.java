package top.jionjion.agentdesk.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * SSE 事件载荷 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatEventDto(
        /* 事件类型, 如 text_chunk, thinking_chunk, tool_call_start 等 */
        String type,
        /* 文本内容 */
        String content,
        /* 工具名称 */
        String toolName,
        /* 工具调用ID */
        String toolId,
        /* 工具调用参数 */
        Map<String, Object> arguments,
        /* 工具调用结果 */
        String result,
        /* 完成原因 */
        String reason,
        /* 错误信息 */
        String error
) {
    /** 文本片段事件 */
    public static ChatEventDto textChunk(String content) {
        return new ChatEventDto("text_chunk", content, null, null, null, null, null, null);
    }

    /** 思考片段事件 */
    public static ChatEventDto thinkingChunk(String content) {
        return new ChatEventDto("thinking_chunk", content, null, null, null, null, null, null);
    }

    /** Agent开始事件 */
    public static ChatEventDto agentStart() {
        return new ChatEventDto("agent_start", null, null, null, null, null, null, null);
    }

    /** 推理完成事件, content标识是否包含工具调用 */
    public static ChatEventDto reasoningComplete(boolean hasToolCalls) {
        return new ChatEventDto("reasoning_complete", String.valueOf(hasToolCalls), null, null, null, null, null, null);
    }

    /** 工具调用开始事件 */
    public static ChatEventDto toolCallStart(String toolName, String toolId, Map<String, Object> arguments) {
        return new ChatEventDto("tool_call_start", null, toolName, toolId, arguments, null, null, null);
    }

    /** 工具调用结束事件 */
    public static ChatEventDto toolCallEnd(String toolName, String toolId, String result) {
        return new ChatEventDto("tool_call_end", null, toolName, toolId, null, result, null, null);
    }

    /** Agent完成事件 */
    public static ChatEventDto agentComplete(String content, String reason) {
        return new ChatEventDto("agent_complete", content, null, null, null, null, reason, null);
    }

    /** 错误事件 */
    public static ChatEventDto error(String message) {
        return new ChatEventDto("error", null, null, null, null, null, null, message);
    }
}
