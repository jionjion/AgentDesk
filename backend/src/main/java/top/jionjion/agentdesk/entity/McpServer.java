package top.jionjion.agentdesk.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import top.jionjion.agentdesk.converter.JsonMapConverter;

import java.util.Map;

/**
 * MCP 服务器配置实体
 *
 * @author Jion
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "mcp_servers", schema = "agent_desk",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "name"}))
public class McpServer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 512)
    private String description;

    /**
     * 传输类型: sse / stdio
     */
    @Column(nullable = false, length = 16)
    private String type;

    /**
     * 传输配置 JSON.
     * SSE: { "url": "...", "headers": { "key": "value" } }
     * StdIO: { "command": [...], "env": { "K": "V" }, "workingDirectory": "..." }
     */
    @Convert(converter = JsonMapConverter.class)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> config;

    @Column(nullable = false)
    private boolean enabled;

    /**
     * 连续连接失败次数. 成功连接后清零, 累计达到阈值时自动禁用 (enabled=false).
     */
    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;
}
