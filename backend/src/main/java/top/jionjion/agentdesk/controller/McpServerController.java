package top.jionjion.agentdesk.controller;

import org.springframework.web.bind.annotation.*;
import top.jionjion.agentdesk.agent.core.AgentPool;
import top.jionjion.agentdesk.dto.mcp.CreateMcpServerRequest;
import top.jionjion.agentdesk.dto.mcp.McpServerDto;
import top.jionjion.agentdesk.dto.mcp.McpTestResultDto;
import top.jionjion.agentdesk.dto.mcp.UpdateMcpServerRequest;
import top.jionjion.agentdesk.security.UserContext;
import top.jionjion.agentdesk.service.McpServerService;

import java.util.List;
import java.util.Map;

/**
 * MCP 服务器管理控制器
 *
 * @author Jion
 */
@RestController
@RequestMapping("/api/mcp-servers")
public class McpServerController {

    private final McpServerService mcpServerService;
    private final AgentPool agentPool;

    public McpServerController(McpServerService mcpServerService, AgentPool agentPool) {
        this.mcpServerService = mcpServerService;
        this.agentPool = agentPool;
    }

    @GetMapping
    public List<McpServerDto> list() {
        return mcpServerService.listServers(UserContext.getUserId());
    }

    @PostMapping
    public McpServerDto create(@RequestBody CreateMcpServerRequest request) {
        Long userId = UserContext.getUserId();
        McpServerDto result = mcpServerService.createServer(request, userId);
        agentPool.invalidateAll(userId);
        return result;
    }

    @PutMapping("/{id}")
    public McpServerDto update(@PathVariable Long id, @RequestBody UpdateMcpServerRequest request) {
        Long userId = UserContext.getUserId();
        McpServerDto result = mcpServerService.updateServer(id, request, userId);
        agentPool.invalidateAll(userId);
        return result;
    }

    @DeleteMapping("/{id}")
    public Map<String, String> delete(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        mcpServerService.deleteServer(id, userId);
        agentPool.invalidateAll(userId);
        return Map.of("message", "MCP 服务器已删除");
    }

    @PutMapping("/{id}/enabled")
    public Map<String, String> setEnabled(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        Long userId = UserContext.getUserId();
        Boolean enabled = body.get("enabled");
        if (enabled == null) {
            enabled = false;
        }
        mcpServerService.setEnabled(id, enabled, userId);
        agentPool.invalidateAll(userId);
        return Map.of("message", enabled ? "已启用" : "已禁用");
    }

    @PostMapping("/{id}/test")
    public McpTestResultDto testConnection(@PathVariable Long id) {
        return mcpServerService.testConnection(id, UserContext.getUserId());
    }
}
