package top.jionjion.agentdesk.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import top.jionjion.agentdesk.agent.exec.ClientExecException;
import top.jionjion.agentdesk.agent.exec.ClientExecutor;
import top.jionjion.agentdesk.websocket.dto.CommandRequest;
import top.jionjion.agentdesk.websocket.dto.CommandResult;
import top.jionjion.agentdesk.websocket.dto.SandboxResult;
import top.jionjion.agentdesk.websocket.dto.WsMessage;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 远程执行桥接器: 管理 WebSocket 连接, 协调命令下发与结果回收。
 * <p>
 * 核心职责:
 * - 维护 userId → WebSocketSession 映射
 * - 发送命令到客户端并阻塞等待结果
 * - 处理超时、拒绝、断连等异常场景
 *
 * @author Jion
 */
@Service
public class RemoteExecBridge implements ClientExecutor {

    private static final Logger log = LoggerFactory.getLogger(RemoteExecBridge.class);

    private final ObjectMapper objectMapper;
    private final long commandTimeout;
    private final int maxPendingCommands;
    private final int maxResultSize;
    private final int execMemoryLimitMb;
    private final int execMaxProcesses;

    /**
     * userId → WebSocketSession 映射（每个用户最多一个活跃连接）
     */
    private final ConcurrentHashMap<Long, WebSocketSession> connections = new ConcurrentHashMap<>();

    /**
     * userId → 客户端平台信息（如 "Windows 10", "macOS", "Linux"）
     */
    private final ConcurrentHashMap<Long, String> clientPlatforms = new ConcurrentHashMap<>();

    /**
     * requestId → CompletableFuture 映射（等待客户端响应）
     */
    private final ConcurrentHashMap<String, PendingCommand> pendingRequests = new ConcurrentHashMap<>();

    /**
     * requestId → CompletableFuture 映射（沙箱执行等待）
     */
    private final ConcurrentHashMap<String, PendingSandbox> pendingSandboxRequests = new ConcurrentHashMap<>();

    private record PendingCommand(Long userId, CompletableFuture<CommandResult> future) {
    }

    private record PendingSandbox(Long userId, CompletableFuture<SandboxResult> future) {
    }

    public RemoteExecBridge(ObjectMapper objectMapper,
                            @Value("${agentdesk.remote-exec.command-timeout:120000}") long commandTimeout,
                            @Value("${agentdesk.remote-exec.max-pending-commands:10}") int maxPendingCommands,
                            @Value("${agentdesk.remote-exec.max-result-size:262144}") int maxResultSize,
                            @Value("${agentdesk.remote-exec.exec-memory-limit-mb:2048}") int execMemoryLimitMb,
                            @Value("${agentdesk.remote-exec.exec-max-processes:64}") int execMaxProcesses) {
        this.objectMapper = objectMapper;
        this.commandTimeout = commandTimeout;
        this.maxPendingCommands = maxPendingCommands;
        this.maxResultSize = maxResultSize;
        this.execMemoryLimitMb = execMemoryLimitMb;
        this.execMaxProcesses = execMaxProcesses;
    }

    // ==================== 连接管理 ====================

    /**
     * 注册客户端连接
     */
    public void registerConnection(Long userId, WebSocketSession session) {
        WebSocketSession existing = connections.put(userId, session);
        if (existing != null && existing.isOpen()) {
            try {
                existing.close();
                log.info("用户 {} 旧连接已关闭, 替换为新连接 {}", userId, session.getId());
            } catch (IOException e) {
                log.warn("关闭旧连接失败: {}", e.getMessage());
            }
        }
        log.info("用户 {} 远程执行客户端已连接, sessionId={}", userId, session.getId());
    }

    /**
     * 注册客户端平台信息（由 CLIENT_READY 消息触发）
     */
    public void registerClientPlatform(Long userId, String platform) {
        if (platform != null && !platform.isBlank()) {
            clientPlatforms.put(userId, platform);
            log.info("用户 {} 客户端平台: {}", userId, platform);
        }
    }

    /**
     * 获取客户端平台信息
     *
     * @return 平台描述（如 "Windows", "macOS", "Linux"），未知时返回 null
     */
    public String getClientPlatform(Long userId) {
        return clientPlatforms.get(userId);
    }

    /**
     * 移除客户端连接, 并完成所有待处理请求
     */
    public void removeConnection(Long userId) {
        connections.remove(userId);
        clientPlatforms.remove(userId);
        // 完成该用户所有 pending 请求（以异常方式）
        pendingRequests.entrySet().removeIf(entry -> {
            PendingCommand pending = entry.getValue();
            if (userId.equals(pending.userId()) && !pending.future().isDone()) {
                pending.future().completeExceptionally(new IOException("客户端已断开连接"));
                return true;
            }
            return false;
        });
        pendingSandboxRequests.entrySet().removeIf(entry -> {
            PendingSandbox pending = entry.getValue();
            if (userId.equals(pending.userId()) && !pending.future().isDone()) {
                pending.future().completeExceptionally(new IOException("客户端已断开连接"));
                return true;
            }
            return false;
        });
        log.info("用户 {} 远程执行客户端已断开", userId);
    }

    /**
     * 检查客户端是否已连接
     */
    public boolean isConnected(Long userId) {
        WebSocketSession session = connections.get(userId);
        return session != null && session.isOpen();
    }

    // ==================== 命令执行 ====================

    /**
     * 向客户端发送命令并阻塞等待结果
     *
     * @param userId     用户 ID
     * @param sessionId  聊天会话 ID
     * @param command    要执行的命令
     * @param workingDir 工作目录（可为 null）
     * @param riskLevel  风险等级
     * @return 命令执行结果
     * @throws RemoteExecException 执行失败时抛出
     */
    public CommandResult executeCommand(Long userId, String sessionId,
                                        String command, String workingDir,
                                        String riskLevel) throws RemoteExecException {
        WebSocketSession ws = connections.get(userId);
        if (ws == null || !ws.isOpen()) {
            throw new RemoteExecException("客户端未连接, 无法执行远程命令。请确保桌面客户端已启动并连接。");
        }

        // 检查并发限制
        long userPendingCount = countPendingForUser(userId);
        if (userPendingCount >= maxPendingCommands) {
            throw new RemoteExecException("待执行命令数已达上限 (" + maxPendingCommands + "), 请等待当前命令完成。");
        }

        String requestId = UUID.randomUUID().toString();
        CompletableFuture<CommandResult> future = new CompletableFuture<>();
        pendingRequests.put(requestId, new PendingCommand(userId, future));

        try {
            // 构建执行隔离策略: 后端只下发服务端可控的资源限制与隔离档位,
            // 目录边界 allowedRoots 由客户端依据用户授权工作目录自行掌握。
            CommandRequest.ExecPolicy policy = new CommandRequest.ExecPolicy(
                    null,
                    new CommandRequest.ResourceLimits(commandTimeout, maxResultSize, execMemoryLimitMb, execMaxProcesses),
                    CommandRequest.ExecPolicy.LEVEL_BOUNDARY
            );

            // 构建命令请求消息
            CommandRequest request = new CommandRequest(command, workingDir, riskLevel, commandTimeout, null, policy);

            Map<String, Object> payload = new HashMap<>();
            payload.put("command", request.command());
            payload.put("workingDir", request.workingDir() != null ? request.workingDir() : "");
            payload.put("riskLevel", request.riskLevel());
            payload.put("timeoutMs", request.timeoutMs());
            payload.put("policy", request.policy());

            WsMessage msg = WsMessage.of(
                    WsMessage.TYPE_COMMAND_REQUEST,
                    requestId,
                    sessionId,
                    payload
            );

            // 发送到客户端
            String json = objectMapper.writeValueAsString(msg);
            ws.sendMessage(new TextMessage(json));
            log.info("已向用户 {} 发送命令: [{}] {}", userId, riskLevel, command);

            // 阻塞等待结果
            CommandResult result = future.get(commandTimeout, TimeUnit.MILLISECONDS);

            // 截断过大的输出
            return truncateIfNeeded(result);

        } catch (TimeoutException e) {
            // 超时: 通知客户端取消
            sendCancel(ws, requestId, sessionId);
            throw new RemoteExecException("命令执行超时 (" + (commandTimeout / 1000) + "秒): " + command);
        } catch (Exception e) {
            if (e.getCause() instanceof RemoteExecException re) {
                throw re;
            }
            throw new RemoteExecException("远程执行异常: " + e.getMessage());
        } finally {
            pendingRequests.remove(requestId);
        }
    }

    // ==================== 客户端响应处理 ====================

    /**
     * 处理客户端返回的命令执行结果
     */
    public void onCommandResult(String requestId, CommandResult result) {
        PendingCommand pending = pendingRequests.get(requestId);
        if (pending != null) {
            pending.future().complete(result);
        } else {
            log.warn("收到未知 requestId 的结果: {}", requestId);
        }
    }

    /**
     * 处理客户端拒绝执行
     */
    public void onCommandRejected(String requestId, String reason) {
        PendingCommand pending = pendingRequests.get(requestId);
        if (pending != null) {
            pending.future().completeExceptionally(new RemoteExecException("用户拒绝执行该命令" +
                    (reason != null && !reason.isBlank() ? ": " + reason : "")));
        }
    }

    // ==================== 沙箱执行 ====================

    /**
     * 向客户端发送 Python 代码，在浏览器端 Pyodide 沙箱中执行并等待结果
     */
    public SandboxResult executeSandbox(Long userId, String sessionId, String code) throws RemoteExecException {
        WebSocketSession ws = connections.get(userId);
        if (ws == null || !ws.isOpen()) {
            throw new RemoteExecException("客户端未连接, 无法执行沙箱代码。");
        }

        long userPendingCount = countPendingForUser(userId);
        if (userPendingCount >= maxPendingCommands) {
            throw new RemoteExecException("待执行命令数已达上限 (" + maxPendingCommands + "), 请等待当前命令完成。");
        }

        String requestId = UUID.randomUUID().toString();
        CompletableFuture<SandboxResult> future = new CompletableFuture<>();
        pendingSandboxRequests.put(requestId, new PendingSandbox(userId, future));

        try {
            WsMessage msg = WsMessage.of(
                    WsMessage.TYPE_SANDBOX_EXEC_REQUEST,
                    requestId,
                    sessionId,
                    Map.of("code", code)
            );

            String json = objectMapper.writeValueAsString(msg);
            ws.sendMessage(new TextMessage(json));
            log.info("已向用户 {} 发送沙箱代码执行请求, requestId={}", userId, requestId);

            // 沙箱执行超时用 commandTimeout（默认 120s）
            return future.get(commandTimeout, TimeUnit.MILLISECONDS);

        } catch (TimeoutException e) {
            throw new RemoteExecException("沙箱代码执行超时 (" + (commandTimeout / 1000) + "秒)");
        } catch (Exception e) {
            if (e.getCause() instanceof RemoteExecException re) {
                throw re;
            }
            throw new RemoteExecException("沙箱执行异常: " + e.getMessage());
        } finally {
            pendingSandboxRequests.remove(requestId);
        }
    }

    /**
     * 处理客户端返回的沙箱执行结果
     */
    public void onSandboxResult(String requestId, SandboxResult result) {
        PendingSandbox pending = pendingSandboxRequests.get(requestId);
        if (pending != null) {
            pending.future().complete(result);
        } else {
            log.warn("收到未知 requestId 的沙箱结果: {}", requestId);
        }
    }

    // ==================== 内部方法 ====================

    private void sendCancel(WebSocketSession ws, String requestId, String sessionId) {
        try {
            WsMessage cancel = WsMessage.cancel(requestId, sessionId);
            ws.sendMessage(new TextMessage(objectMapper.writeValueAsString(cancel)));
        } catch (IOException e) {
            log.warn("发送取消命令失败: {}", e.getMessage());
        }
    }

    private CommandResult truncateIfNeeded(CommandResult result) {
        String stdout = result.stdout();
        String stderr = result.stderr();
        boolean truncated = false;

        if (stdout != null && stdout.length() > maxResultSize) {
            stdout = stdout.substring(0, maxResultSize) + "\n[输出已截断, 共 " + result.stdout().length() + " 字符]";
            truncated = true;
        }
        if (stderr != null && stderr.length() > maxResultSize) {
            stderr = stderr.substring(0, maxResultSize) + "\n[错误输出已截断, 共 " + result.stderr().length() + " 字符]";
            truncated = true;
        }

        if (truncated) {
            return new CommandResult(result.exitCode(), stdout, stderr, result.durationMs());
        }
        return result;
    }

    private long countPendingForUser(Long userId) {
        long commandCount = pendingRequests.values().stream()
                .filter(pending -> userId.equals(pending.userId()))
                .count();
        long sandboxCount = pendingSandboxRequests.values().stream()
                .filter(pending -> userId.equals(pending.userId()))
                .count();
        return commandCount + sandboxCount;
    }

    /**
     * 远程执行异常
     */
    public static class RemoteExecException extends ClientExecException {
        public RemoteExecException(String message) {
            super(message);
        }
    }
}
