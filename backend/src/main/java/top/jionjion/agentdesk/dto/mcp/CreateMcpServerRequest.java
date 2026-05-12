package top.jionjion.agentdesk.dto.mcp;

import java.util.Map;

/**
 * 创建 MCP 服务器请求
 *
 * @param name        服务器名称
 * @param description 服务器描述
 * @param type        服务器类型
 * @param config      配置参数
 * @author Jion
 */
public record CreateMcpServerRequest(
        String name,
        String description,
        String type,
        Map<String, Object> config
) {
}
