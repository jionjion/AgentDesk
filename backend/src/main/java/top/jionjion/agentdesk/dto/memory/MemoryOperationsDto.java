package top.jionjion.agentdesk.dto.memory;

import java.util.Map;

public record MemoryOperationsDto(Map<String, Long> jobCounts, long activeMemories,
                                  long conflictedMemories, long expiredMemories,
                                  long recallEvents, String providerCircuitState,
                                  int providerConsecutiveFailures, long providerCircuitOpenUntil) {
}
