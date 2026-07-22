package top.jionjion.agentdesk.dto.memory;

public record MemoryJobDto(Long id, String sessionId, String projectId, Long userMessageId,
                           String status, int attempts, long nextAttemptAt,
                           String lastError, long createdAt, long updatedAt) {
}
