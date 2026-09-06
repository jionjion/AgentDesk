package top.jionjion.agentdesk.dto.memory;

/**
 * 手动添加记忆请求
 *
 * @param content 记忆内容
 * @author Jion
 */
public record AddMemoryRequest(String content, String scopeType, String scopeId, String category,
                               Long validUntil, Double importance, Boolean sensitiveConfirmed) {
}
