package top.jionjion.agentdesk.dto.mcp;

import java.util.Map;

/**
 * MCP 服务器响应 DTO
 *
 * @param id          服务器ID
 * @param name        服务器名称
 * @param description 服务器描述
 * @param type        服务器类型
 * @param config      配置参数
 * @param enabled     是否启用
 * @param failureCount 连续连接失败次数 (达到阈值后自动禁用)
 * @param createdAt   创建时间戳
 * @param updatedAt   更新时间戳
 * @author Jion
 */
public record McpServerDto(
        Long id,
        String name,
        String description,
        String type,
        Map<String, Object> config,
        boolean enabled,
        int failureCount,
        long createdAt,
        long updatedAt
) {
}
