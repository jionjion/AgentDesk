package top.jionjion.agentdesk.dto.mcp;

import java.util.Map;

/**
 * MCP 服务器响应 DTO
 */
public record McpServerDto(
        Long id,
        String name,
        String description,
        String type,
        Map<String, Object> config,
        boolean enabled,
        long createdAt,
        long updatedAt
) {
}
