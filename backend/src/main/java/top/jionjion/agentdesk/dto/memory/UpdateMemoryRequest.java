package top.jionjion.agentdesk.dto.memory;

public record UpdateMemoryRequest(String content, String scopeType, String scopeId, String category,
                                  Double importance, Long validUntil, Boolean sensitiveConfirmed) {
}
