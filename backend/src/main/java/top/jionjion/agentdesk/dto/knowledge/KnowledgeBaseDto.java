package top.jionjion.agentdesk.dto.knowledge;

public record KnowledgeBaseDto(
        Long id,
        String name,
        String description,
        int docCount,
        int chunkCount,
        String status,
        long createdAt,
        long updatedAt
) {
}
