package top.jionjion.agentdesk.dto.chat;

/**
 * 流式对话请求体
 */
public record ChatRequest(
        String sessionId,
        String message,
        String fileIds,
        String kbIds
) {
}
