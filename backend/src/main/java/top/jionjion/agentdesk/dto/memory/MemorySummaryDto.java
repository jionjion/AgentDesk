package top.jionjion.agentdesk.dto.memory;

import java.util.List;
import java.util.Map;

public record MemorySummaryDto(String scopeType, String scopeId, long total, long pinned,
                               Long updatedAt, Map<String, Long> categoryCounts,
                               List<MemoryItemDto> highlights) {
}
