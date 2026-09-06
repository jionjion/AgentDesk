package top.jionjion.agentdesk.dto.memory;

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
        String updatedAt,
        String scopeType,
        String scopeId,
        String category,
        String status,
        double confidence,
        double importance,
        boolean pinned,
        long sourceCount,
        String recallReason,
        double score,
        String sensitivity,
        String writePolicy,
        Long validUntil,
        Long supersedesId,
        String subjectKey
) {
    public MemoryItemDto(String id, String memory, String createdAt, String updatedAt) {
        this(id, memory, createdAt, updatedAt, "USER", null, "OTHER", "ACTIVE",
                1d, 0.5d, false, 0L, null, 0d,
                "NORMAL", "EXPLICIT", null, null, null);
    }
}
