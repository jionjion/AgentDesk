package top.jionjion.agentdesk.dto.memory;

public record MemoryRevisionDto(String id, String oldContent, String newContent,
                                String reason, String actor, long createdAt) {
}
