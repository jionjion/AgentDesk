package top.jionjion.agentdesk.dto.session;

/**
 * 会话响应
 *
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
        long lastUsedAt,
        /** 可选关联项目ID */
        String projectId,
        /** 可选关联项目名称 */
        String projectName,
        /** 新轮次默认记忆策略 */
        String memoryMode) {
}
