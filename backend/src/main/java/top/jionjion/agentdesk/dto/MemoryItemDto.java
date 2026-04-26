package top.jionjion.agentdesk.dto;

/**
 * Mem0 记忆条目 DTO
 *
 * @param id        记忆ID
 * @param memory    记忆内容
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 * @author Jion
 */
public record MemoryItemDto(
        String id,
        String memory,
        String createdAt,
        String updatedAt
) {
}
