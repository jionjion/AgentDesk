package top.jionjion.agentdesk.dto.mcp;

import java.util.List;

/**
 * MCP 连接测试结果 DTO
 *
 * @param success        是否连接成功
 * @param message        结果消息
 * @param availableTools 可用工具列表
 * @param latencyMs      延迟(毫秒)
 * @author Jion
 */
public record McpTestResultDto(
        boolean success,
        String message,
        List<String> availableTools,
        long latencyMs
) {
}
