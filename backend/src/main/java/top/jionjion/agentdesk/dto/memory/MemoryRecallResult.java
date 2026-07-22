package top.jionjion.agentdesk.dto.memory;

import java.util.List;

/** Result of one bounded memory recall operation. */
public record MemoryRecallResult(
        String status,
        List<MemoryItemDto> items,
        long elapsedMs,
        String degradedReason
) {
    public static MemoryRecallResult disabled(String reason, long elapsedMs) {
        return new MemoryRecallResult("DISABLED", List.of(), elapsedMs, reason);
    }

    public static MemoryRecallResult degraded(String reason, long elapsedMs) {
        return new MemoryRecallResult("DEGRADED", List.of(), elapsedMs, reason);
    }
}
