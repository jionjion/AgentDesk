package top.jionjion.agentdesk.dto.session;

/**
 * 会话响应
 *
 * @param id         会话ID
 * @param title      会话标题
 * @param createdAt  创建时间戳
 * @param lastUsedAt 最后使用时间戳
 * @author Jion
 */
public record SessionResponse(
        /** 会话ID */
        String id,
        /** 会话标题 */
        String title,
        /** 创建时间戳 */
        long createdAt,
        /** 最后使用时间戳 */
        long lastUsedAt) {
}
