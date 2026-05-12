package top.jionjion.agentdesk.dto.chat;

/**
 * 消息搜索结果
 *
 * @param id           消息ID
 * @param sessionId    会话ID
 * @param sessionTitle 会话标题
 * @param role         消息角色
 * @param content      消息内容
 * @param createdAt    创建时间戳
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
