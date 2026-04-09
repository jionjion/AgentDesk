package top.jionjion.agentdesk.dto;

/**
 * 消息搜索结果
 */
public record SearchResultDto(
        Long id,
        String sessionId,
        String sessionTitle,
        String role,
        String content,
        long createdAt
) {
}
