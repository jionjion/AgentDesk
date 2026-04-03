package top.jionjion.agentdesk.dto;

/**
 * 会话响应
 */
public record SessionResponse(
        String id,
        String title,
        long createdAt,
        long lastUsedAt) {
}
