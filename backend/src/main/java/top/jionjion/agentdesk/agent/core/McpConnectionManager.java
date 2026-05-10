package top.jionjion.agentdesk.agent.core;

import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import top.jionjion.agentdesk.dto.mcp.McpTestResultDto;
import top.jionjion.agentdesk.entity.McpServer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MCP 连接管理器: 封装 AgentScope MCP SDK 的连接、测试、注册操作
 *
 * @author Jion
 */
@Component
public class McpConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(McpConnectionManager.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    /**
     * 测试 MCP 服务器连接, 返回测试结果
     */
    public McpTestResultDto testConnection(McpServer server) {
        long start = System.currentTimeMillis();
        McpClientWrapper client = null;
        try {
            client = buildClient(server);
            // 获取可用工具列表
            Set<String> toolNames = getToolNames(client);
            long latency = System.currentTimeMillis() - start;
            return new McpTestResultDto(true, "连接成功", new ArrayList<>(toolNames), latency);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            log.warn("MCP 服务器 [{}] 连接测试失败: {}", server.getName(), e.getMessage());
            return new McpTestResultDto(false, e.getMessage(), List.of(), latency);
        } finally {
            closeQuietly(client);
        }
    }

    /**
     * 将已启用的 MCP 服务器连接并注册到 Toolkit 中.
     * 连接失败时记录日志并跳过, 不影响 Agent 创建.
     */
    public void connectAndRegister(Toolkit toolkit, List<McpServer> servers) {
        for (McpServer server : servers) {
            try {
                McpClientWrapper client = buildClient(server);
                toolkit.registerMcpClient(client).block();
                log.info("MCP 服务器 [{}] 已连接并注册 (type={})", server.getName(), server.getType());
            } catch (Exception e) {
                log.warn("MCP 服务器 [{}] 连接失败, 已跳过: {}", server.getName(), e.getMessage());
            }
        }
    }

    /**
     * 根据服务器配置构建 MCP 客户端
     */
    private McpClientWrapper buildClient(McpServer server) {
        Map<String, Object> config = server.getConfig();
        String name = server.getName();

        return switch (server.getType()) {
            case "sse" -> buildSseClient(name, config);
            case "stdio" -> buildStdioClient(name, config);
            default -> throw new IllegalArgumentException("不支持的 MCP 传输类型: " + server.getType());
        };
    }

    private McpClientWrapper buildSseClient(String name, Map<String, Object> config) {
        String url = (String) config.get("url");
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("SSE 配置缺少 url");
        }

        McpClientBuilder builder = McpClientBuilder.create(name)
                .sseTransport(url)
                .timeout(CONNECT_TIMEOUT);

        // 添加 headers
        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) config.get("headers");
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder.header(entry.getKey(), entry.getValue());
            }
        }

        return builder.buildAsync().block();
    }

    private McpClientWrapper buildStdioClient(String name, Map<String, Object> config) {
        @SuppressWarnings("unchecked")
        List<String> command = (List<String>) config.get("command");
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("StdIO 配置缺少 command");
        }

        String program = command.get(0);
        String[] args = command.subList(1, command.size()).toArray(new String[0]);
        McpClientBuilder builder = McpClientBuilder.create(name)
                .stdioTransport(program, args)
                .timeout(CONNECT_TIMEOUT);

        return builder.buildAsync().block();
    }

    /**
     * 获取 MCP 客户端的可用工具名称
     */
    private Set<String> getToolNames(McpClientWrapper client) {
        // 通过临时 Toolkit 注册后获取工具名
        Toolkit tempToolkit = new Toolkit();
        tempToolkit.registerMcpClient(client).block();
        return tempToolkit.getToolNames();
    }

    private void closeQuietly(McpClientWrapper client) {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                log.debug("关闭 MCP 客户端时出错: {}", e.getMessage());
            }
        }
    }
}
