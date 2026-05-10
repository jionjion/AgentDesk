package top.jionjion.agentdesk.dto.mcp;

import java.util.List;

/**
 * MCP 连接测试结果 DTO
 */
public record McpTestResultDto(
        boolean success,
        String message,
        List<String> availableTools,
        long latencyMs
) {
}
