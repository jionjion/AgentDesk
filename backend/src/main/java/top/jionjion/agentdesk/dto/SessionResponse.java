package top.jionjion.agentdesk.dto;

/**
 * 会话响应
 */
public record SessionResponse(
        /* 会话ID */
        String id,
        /* 会话标题 */
        String title,
        /* 创建时间戳 */
        long createdAt,
        /* 最后使用时间戳 */
        long lastUsedAt) {
}
