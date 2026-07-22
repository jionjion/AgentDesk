package top.jionjion.agentdesk.dto.memory;

public record MemorySourceDto(
        String id,
        String sourceType,
        String sourceId,
        String sessionId,
        String projectId,
        String evidenceExcerpt,
        String trustLevel,
        long createdAt
) {
}
