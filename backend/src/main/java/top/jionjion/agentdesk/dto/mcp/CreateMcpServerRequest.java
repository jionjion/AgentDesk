package top.jionjion.agentdesk.dto.mcp;

import java.util.Map;

/**
 * 创建 MCP 服务器请求
 */
public record CreateMcpServerRequest(
        String name,
        String description,
        String type,
        Map<String, Object> config
) {
}
