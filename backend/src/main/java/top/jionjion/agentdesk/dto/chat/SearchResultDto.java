package top.jionjion.agentdesk.dto.chat;

/**
 * 消息搜索结果
 *
 * @author Jion
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
