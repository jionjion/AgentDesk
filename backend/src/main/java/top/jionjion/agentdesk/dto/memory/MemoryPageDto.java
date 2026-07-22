package top.jionjion.agentdesk.dto.memory;

import java.util.List;

public record MemoryPageDto(List<MemoryItemDto> items, long total, int page, int pageSize) {
}
