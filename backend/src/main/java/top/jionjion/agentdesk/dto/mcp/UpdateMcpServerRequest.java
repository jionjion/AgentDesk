package top.jionjion.agentdesk.dto.mcp;

import java.util.Map;

/**
 * 更新 MCP 服务器请求
 */
public record UpdateMcpServerRequest(
        String name,
        String description,
        String type,
        Map<String, Object> config
) {
}
