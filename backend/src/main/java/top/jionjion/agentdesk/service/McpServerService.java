package top.jionjion.agentdesk.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import top.jionjion.agentdesk.agent.core.McpConnectionManager;
import top.jionjion.agentdesk.dto.mcp.CreateMcpServerRequest;
import top.jionjion.agentdesk.dto.mcp.McpServerDto;
import top.jionjion.agentdesk.dto.mcp.McpTestResultDto;
import top.jionjion.agentdesk.dto.mcp.UpdateMcpServerRequest;
import top.jionjion.agentdesk.entity.McpServer;
import top.jionjion.agentdesk.repository.McpServerRepository;

import java.util.List;
import java.util.Map;

/**
 * MCP 服务器管理服务
 *
 * @author Jion
 */
@Service
public class McpServerService {

    private static final Logger log = LoggerFactory.getLogger(McpServerService.class);

    /**
     * 连续失败达到此阈值时, 自动禁用 MCP 服务器 (熔断)
     */
    private static final int FAILURE_THRESHOLD = 3;

    private final McpServerRepository repository;
    private final McpConnectionManager connectionManager;

    public McpServerService(McpServerRepository repository,
                            McpConnectionManager connectionManager) {
        this.repository = repository;
        this.connectionManager = connectionManager;
    }

    /**
     * 获取用户的所有 MCP 服务器配置
     */
    public List<McpServerDto> listServers(Long userId) {
        return repository.findByUserId(userId).stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * 获取用户已启用的 MCP 服务器列表 (供 AgentFactory 调用)
     */
    public List<McpServer> getEnabledServers(Long userId) {
        return repository.findByUserIdAndEnabledTrue(userId);
    }

    /**
     * 创建 MCP 服务器配置
     */
    @Transactional
    public McpServerDto createServer(CreateMcpServerRequest request, Long userId) {
        validateRequest(request.name(), request.type(), request.config());

        if (repository.existsByUserIdAndName(userId, request.name())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "同名 MCP 服务器已存在");
        }

        long now = System.currentTimeMillis();
        McpServer server = new McpServer();
        server.setUserId(userId);
        server.setName(request.name());
        server.setDescription(request.description());
        server.setType(request.type());
        server.setConfig(request.config());
        server.setEnabled(true);
        server.setFailureCount(0);
        server.setCreatedAt(now);
        server.setUpdatedAt(now);

        return toDto(repository.save(server));
    }

    /**
     * 更新 MCP 服务器配置
     */
    @Transactional
    public McpServerDto updateServer(Long id, UpdateMcpServerRequest request, Long userId) {
        McpServer server = findByIdAndUser(id, userId);

        validateRequest(request.name(), request.type(), request.config());

        // 如果修改了名称, 需要检查唯一性
        if (!server.getName().equals(request.name()) && repository.existsByUserIdAndName(userId, request.name())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "同名 MCP 服务器已存在");
        }

        server.setName(request.name());
        server.setDescription(request.description());
        server.setType(request.type());
        server.setConfig(request.config());
        // 配置已更新, 重置失败计数, 给新配置一次重新连接的机会
        server.setFailureCount(0);
        server.setUpdatedAt(System.currentTimeMillis());

        return toDto(repository.save(server));
    }

    /**
     * 删除 MCP 服务器配置
     */
    @Transactional
    public void deleteServer(Long id, Long userId) {
        McpServer server = findByIdAndUser(id, userId);
        repository.delete(server);
    }

    /**
     * 切换启用/禁用状态
     */
    @Transactional
    public void setEnabled(Long id, boolean enabled, Long userId) {
        McpServer server = findByIdAndUser(id, userId);
        server.setEnabled(enabled);
        // 手动重新启用时清空失败计数, 给服务器一次新机会
        if (enabled) {
            server.setFailureCount(0);
        }
        server.setUpdatedAt(System.currentTimeMillis());
        repository.save(server);
    }

    /**
     * 根据连接结果更新失败计数并执行熔断.
     * 由 AgentFactory 在连接 MCP 服务器后调用.
     * 成功: 失败计数清零;
     * 失败: 失败计数 +1, 累计达到阈值时自动禁用 (enabled=false).
     *
     * @param results 服务器ID -> 是否连接成功
     */
    @Transactional
    public void applyConnectionResults(Map<Long, Boolean> results) {
        for (Map.Entry<Long, Boolean> entry : results.entrySet()) {
            repository.findById(entry.getKey()).ifPresent(server -> {
                if (entry.getValue()) {
                    // 连接成功: 清零计数
                    if (server.getFailureCount() != 0) {
                        server.setFailureCount(0);
                        server.setUpdatedAt(System.currentTimeMillis());
                        repository.save(server);
                    }
                } else {
                    // 连接失败: 累加并判断熔断
                    int failures = server.getFailureCount() + 1;
                    server.setFailureCount(failures);
                    if (failures >= FAILURE_THRESHOLD) {
                        server.setEnabled(false);
                        log.warn("MCP 服务器 [{}] 连续失败 {} 次, 已自动禁用", server.getName(), failures);
                    }
                    server.setUpdatedAt(System.currentTimeMillis());
                    repository.save(server);
                }
            });
        }
    }

    /**
     * 测试 MCP 服务器连接
     */
    public McpTestResultDto testConnection(Long id, Long userId) {
        McpServer server = findByIdAndUser(id, userId);
        return connectionManager.testConnection(server);
    }

    // ========== 内部方法 ==========

    private McpServer findByIdAndUser(Long id, Long userId) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MCP 服务器不存在"));
    }

    private void validateRequest(String name, String type, Map<String, Object> config) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "名称不能为空");
        }
        if (type == null || (!type.equals("sse") && !type.equals("stdio"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "类型必须是 sse 或 stdio");
        }
        if (config == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "配置不能为空");
        }

        if ("sse".equals(type)) {
            Object url = config.get("url");
            if (url == null || url.toString().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SSE 类型必须提供 url");
            }
        } else {
            Object command = config.get("command");
            if (!(command instanceof List<?> cmdList) || cmdList.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "StdIO 类型必须提供非空的 command 列表");
            }
        }
    }

    private McpServerDto toDto(McpServer server) {
        return new McpServerDto(
                server.getId(),
                server.getName(),
                server.getDescription(),
                server.getType(),
                server.getConfig(),
                server.isEnabled(),
                server.getFailureCount(),
                server.getCreatedAt(),
                server.getUpdatedAt()
        );
    }
}
