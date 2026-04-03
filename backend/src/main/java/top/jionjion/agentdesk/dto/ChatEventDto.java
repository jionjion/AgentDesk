package top.jionjion.agentdesk.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * SSE 事件载荷 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatEventDto(
        String type,
        String content,
        String toolName,
        String toolId,
        Map<String, Object> arguments,
        String result,
        String reason,
        String error
) {
    public static ChatEventDto textChunk(String content) {
        return new ChatEventDto("text_chunk", content, null, null, null, null, null, null);
    }

    public static ChatEventDto thinkingChunk(String content) {
        return new ChatEventDto("thinking_chunk", content, null, null, null, null, null, null);
    }

    public static ChatEventDto agentStart() {
        return new ChatEventDto("agent_start", null, null, null, null, null, null, null);
    }

    public static ChatEventDto reasoningComplete(boolean hasToolCalls) {
        return new ChatEventDto("reasoning_complete", String.valueOf(hasToolCalls), null, null, null, null, null, null);
    }

    public static ChatEventDto toolCallStart(String toolName, String toolId, Map<String, Object> arguments) {
        return new ChatEventDto("tool_call_start", null, toolName, toolId, arguments, null, null, null);
    }

    public static ChatEventDto toolCallEnd(String toolName, String toolId, String result) {
        return new ChatEventDto("tool_call_end", null, toolName, toolId, null, result, null, null);
    }

    public static ChatEventDto agentComplete(String content, String reason) {
        return new ChatEventDto("agent_complete", content, null, null, null, null, reason, null);
    }

    public static ChatEventDto error(String message) {
        return new ChatEventDto("error", null, null, null, null, null, null, message);
    }
}
