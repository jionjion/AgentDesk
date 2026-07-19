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
import top.jionjion.agentdesk.agent.exec.ExecSpec;
import top.jionjion.agentdesk.websocket.dto.CommandRequest;
import top.jionjion.agentdesk.websocket.dto.CommandResult;
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
 * 远程执行桥接器: 管理 WebSocket 连接, 协调统一本地执行请求 (shell/python)、
 * 本地文件 RPC 与定时任务 runtime snapshot 的下发与结果回收。
 * <p>
 * 核心职责:
 * - 维护 userId → WebSocketSession 映射
 * - 发送请求到客户端并阻塞等待结果
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
    private final long localFsTimeout;

    /**
     * userId → WebSocketSession 映射（每个用户最多一个活跃连接）
     */
    private final ConcurrentHashMap<Long, WebSocketSession> connections = new ConcurrentHashMap<>();

    /**
     * userId → 客户端平台信息（如 "Windows 10", "macOS", "Linux"）
     */
    private final ConcurrentHashMap<Long, String> clientPlatforms = new ConcurrentHashMap<>();

    /**
     * userId → 客户端设备ID（client_ready 上报; MVP 每用户最多一个在线设备）
     */
    private final ConcurrentHashMap<Long, String> clientDeviceIds = new ConcurrentHashMap<>();

    /**
     * userId → 客户端能力集（client_ready 上报, 如 shell/python/local_files）
     */
    private final ConcurrentHashMap<Long, java.util.List<String>> clientCapabilities = new ConcurrentHashMap<>();

    /**
     * requestId → CompletableFuture 映射（等待客户端执行结果）
     */
    private final ConcurrentHashMap<String, PendingCommand> pendingRequests = new ConcurrentHashMap<>();

    /**
     * requestId → CompletableFuture 映射（本地文件 RPC 等待）
     */
    private final ConcurrentHashMap<String, PendingFs> pendingFsRequests = new ConcurrentHashMap<>();

    /**
     * requestId → CompletableFuture 映射（runtime snapshot 等待, 定时任务用）
     */
    private final ConcurrentHashMap<String, PendingSnapshot> pendingSnapshotRequests = new ConcurrentHashMap<>();

    private record PendingCommand(Long userId, CompletableFuture<CommandResult> future) {
    }

    private record PendingFs(Long userId, CompletableFuture<Map<String, Object>> future) {
    }

    private record PendingSnapshot(Long userId, CompletableFuture<Map<String, Object>> future) {
    }

    public RemoteExecBridge(ObjectMapper objectMapper,
                            @Value("${agentdesk.remote-exec.command-timeout:120000}") long commandTimeout,
                            @Value("${agentdesk.remote-exec.max-pending-commands:10}") int maxPendingCommands,
                            @Value("${agentdesk.remote-exec.max-result-size:262144}") int maxResultSize,
                            @Value("${agentdesk.remote-exec.exec-memory-limit-mb:2048}") int execMemoryLimitMb,
                            @Value("${agentdesk.remote-exec.exec-max-processes:64}") int execMaxProcesses,
                            @Value("${agentdesk.remote-exec.local-fs-timeout:30000}") long localFsTimeout) {
        this.objectMapper = objectMapper;
        this.commandTimeout = commandTimeout;
        this.maxPendingCommands = maxPendingCommands;
        this.maxResultSize = maxResultSize;
        this.execMemoryLimitMb = execMemoryLimitMb;
        this.execMaxProcesses = execMaxProcesses;
        this.localFsTimeout = localFsTimeout;
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
     * 注册客户端设备ID（由 CLIENT_READY 消息触发）
     */
    public void registerClientDeviceId(Long userId, String deviceId) {
        if (deviceId != null && !deviceId.isBlank()) {
            clientDeviceIds.put(userId, deviceId);
            log.info("用户 {} 客户端设备: {}", userId, deviceId);
        }
    }

    /**
     * 注册客户端能力集（由 CLIENT_READY 消息触发, 见开发计划 8.1）
     */
    public void registerClientCapabilities(Long userId, java.util.List<String> capabilities) {
        if (capabilities != null && !capabilities.isEmpty()) {
            clientCapabilities.put(userId, java.util.List.copyOf(capabilities));
            log.info("用户 {} 客户端能力: {}", userId, capabilities);
        }
    }

    /**
     * 获取客户端设备ID, 未上报时返回 null
     */
    public String getClientDeviceId(Long userId) {
        return clientDeviceIds.get(userId);
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
        clientDeviceIds.remove(userId);
        clientCapabilities.remove(userId);
        // 完成该用户所有 pending 请求（以异常方式）
        pendingRequests.entrySet().removeIf(entry -> {
            PendingCommand pending = entry.getValue();
            if (userId.equals(pending.userId()) && !pending.future().isDone()) {
                pending.future().completeExceptionally(new IOException("客户端已断开连接"));
                return true;
            }
            return false;
        });
        pendingFsRequests.entrySet().removeIf(entry -> {
            PendingFs pending = entry.getValue();
            if (userId.equals(pending.userId()) && !pending.future().isDone()) {
                pending.future().completeExceptionally(new IOException("客户端已断开连接"));
                return true;
            }
            return false;
        });
        pendingSnapshotRequests.entrySet().removeIf(entry -> {
            PendingSnapshot pending = entry.getValue();
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

    // ==================== 统一本地执行 (shell / python) ====================

    /**
     * 向客户端发送统一本地执行请求并阻塞等待结果 (见开发计划 8.2)
     */
    @Override
    public CommandResult executeExec(Long userId, String sessionId, ExecSpec spec) throws ClientExecException {
        WebSocketSession ws = connections.get(userId);
        if (ws == null || !ws.isOpen()) {
            throw new RemoteExecException("客户端未连接, 无法执行本地命令。请确保桌面客户端已启动并连接。");
        }

        // 检查并发限制
        long userPendingCount = countPendingForUser(userId);
        if (userPendingCount >= maxPendingCommands) {
            throw new RemoteExecException("待执行命令数已达上限 (" + maxPendingCommands + "), 请等待当前命令完成。");
        }

        long timeoutMs = spec.timeoutMs() != null && spec.timeoutMs() > 0
                ? Math.min(spec.timeoutMs(), commandTimeout) : commandTimeout;

        String requestId = UUID.randomUUID().toString();
        CompletableFuture<CommandResult> future = new CompletableFuture<>();
        pendingRequests.put(requestId, new PendingCommand(userId, future));

        try {
            // 资源限制由服务端下发; 目录不再作为安全边界 (见开发计划 10.1)
            CommandRequest.ResourceLimits limits = new CommandRequest.ResourceLimits(
                    timeoutMs, maxResultSize, execMemoryLimitMb, execMaxProcesses);

            Map<String, Object> payload = new HashMap<>();
            payload.put("kind", spec.kind());
            payload.put("command", spec.command());
            payload.put("code", spec.code());
            payload.put("scriptPath", spec.scriptPath());
            payload.put("args", spec.args());
            payload.put("cwd", spec.cwd() != null ? spec.cwd() : "");
            payload.put("projectId", spec.projectId());
            payload.put("deviceId", spec.deviceId());
            payload.put("riskLevel", spec.riskLevel());
            payload.put("timeoutMs", timeoutMs);
            payload.put("resourceLimits", limits);

            WsMessage msg = WsMessage.of(
                    WsMessage.TYPE_COMMAND_REQUEST,
                    requestId,
                    sessionId,
                    payload
            );

            // 发送到客户端
            String json = objectMapper.writeValueAsString(msg);
            ws.sendMessage(new TextMessage(json));
            log.info("已向用户 {} 发送本地执行请求: kind={}, risk={}", userId, spec.kind(), spec.riskLevel());

            // 阻塞等待结果
            CommandResult result = future.get(timeoutMs, TimeUnit.MILLISECONDS);

            // 截断过大的输出
            return truncateIfNeeded(result);

        } catch (TimeoutException e) {
            // 超时: 通知客户端取消
            sendCancel(ws, requestId, sessionId);
            throw new RemoteExecException("本地执行超时 (" + (timeoutMs / 1000) + "秒)");
        } catch (Exception e) {
            if (e.getCause() instanceof RemoteExecException re) {
                throw re;
            }
            throw new RemoteExecException("本地执行异常: " + e.getMessage());
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

    // ==================== 本地文件 RPC ====================

    /**
     * 向客户端发送本地文件 RPC 请求并阻塞等待结果 (见开发计划 8.3)。
     * 写入/编辑类操作可能进入客户端审批队列, 等待时间放宽到命令超时。
     */
    @Override
    public Map<String, Object> executeLocalFs(Long userId, String sessionId,
                                              Map<String, Object> request) throws ClientExecException {
        WebSocketSession ws = connections.get(userId);
        if (ws == null || !ws.isOpen()) {
            throw new RemoteExecException("客户端未连接, 无法访问本地文件。请确保桌面客户端已启动并连接。");
        }

        String op = String.valueOf(request.get("op"));
        boolean needsApproval = "write".equals(op) || "edit".equals(op);
        long timeoutMs = needsApproval ? commandTimeout : localFsTimeout;

        String requestId = UUID.randomUUID().toString();
        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        pendingFsRequests.put(requestId, new PendingFs(userId, future));
        try {
            WsMessage msg = WsMessage.of(WsMessage.TYPE_LOCAL_FS_REQUEST, requestId, sessionId, request);
            ws.sendMessage(new TextMessage(objectMapper.writeValueAsString(msg)));
            log.info("已向用户 {} 发送本地文件请求: op={}", userId, op);
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new RemoteExecException("本地文件操作超时 (" + (timeoutMs / 1000) + "秒)");
        } catch (Exception e) {
            if (e.getCause() instanceof RemoteExecException re) {
                throw re;
            }
            throw new RemoteExecException("本地文件操作异常: " + e.getMessage());
        } finally {
            pendingFsRequests.remove(requestId);
        }
    }

    /**
     * 处理客户端返回的本地文件 RPC 结果
     */
    public void onLocalFsResult(String requestId, Map<String, Object> payload) {
        PendingFs pending = pendingFsRequests.get(requestId);
        if (pending != null) {
            pending.future().complete(payload);
        } else {
            log.warn("收到未知 requestId 的本地文件结果: {}", requestId);
        }
    }

    // ==================== Runtime Snapshot (定时任务) ====================

    /**
     * 向指定用户在线客户端请求项目 runtime snapshot (定时任务用, 见开发计划 8.4)。
     *
     * @param userId    任务所属用户
     * @param projectId 项目ID
     * @param deviceId  任务保存的目标设备ID
     * @param taskId    定时任务ID
     * @param timeoutMs snapshot 独立短超时 (不占用 Agent 执行超时)
     * @return snapshot payload (rootPath/cwd/platform/pythonExecutable/pythonVersion 等)
     * @throws SnapshotException 稳定错误码: RUNTIME_OFFLINE / DEVICE_MISMATCH / PROJECT_LOCATION_NOT_FOUND / SNAPSHOT_TIMEOUT
     */
    public Map<String, Object> requestRuntimeSnapshot(Long userId, String projectId, String deviceId,
                                                      Long taskId, long timeoutMs) throws SnapshotException {
        WebSocketSession ws = connections.get(userId);
        if (ws == null || !ws.isOpen()) {
            throw new SnapshotException(SnapshotException.RUNTIME_OFFLINE, "客户端不在线");
        }
        String onlineDeviceId = clientDeviceIds.get(userId);
        if (deviceId != null && onlineDeviceId != null && !deviceId.equals(onlineDeviceId)) {
            throw new SnapshotException(SnapshotException.DEVICE_MISMATCH,
                    "任务目标设备 " + deviceId + " 与当前在线设备 " + onlineDeviceId + " 不一致");
        }

        String requestId = UUID.randomUUID().toString();
        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        pendingSnapshotRequests.put(requestId, new PendingSnapshot(userId, future));
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("taskId", taskId);
            payload.put("projectId", projectId);
            payload.put("deviceId", deviceId);
            WsMessage msg = WsMessage.of(WsMessage.TYPE_RUNTIME_SNAPSHOT_REQUEST, requestId, null, payload);
            ws.sendMessage(new TextMessage(objectMapper.writeValueAsString(msg)));
            log.info("已向用户 {} 请求项目 {} runtime snapshot (task={})", userId, projectId, taskId);

            Map<String, Object> result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            if (result == null || Boolean.FALSE.equals(result.get("found"))) {
                throw new SnapshotException(SnapshotException.PROJECT_LOCATION_NOT_FOUND,
                        "设备上未找到项目 " + projectId + " 的本地位置");
            }
            return result;
        } catch (TimeoutException e) {
            throw new SnapshotException(SnapshotException.SNAPSHOT_TIMEOUT,
                    "snapshot 请求超时 (" + timeoutMs + "ms)");
        } catch (SnapshotException e) {
            throw e;
        } catch (Exception e) {
            throw new SnapshotException(SnapshotException.RUNTIME_OFFLINE, "snapshot 请求异常: " + e.getMessage());
        } finally {
            pendingSnapshotRequests.remove(requestId);
        }
    }

    /**
     * 处理客户端返回的 runtime snapshot 结果
     */
    public void onRuntimeSnapshotResult(String requestId, Map<String, Object> payload) {
        PendingSnapshot pending = pendingSnapshotRequests.get(requestId);
        if (pending != null) {
            pending.future().complete(payload);
        } else {
            log.warn("收到未知 requestId 的 snapshot 结果: {}", requestId);
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
        return pendingRequests.values().stream()
                .filter(pending -> userId.equals(pending.userId()))
                .count();
    }

    /**
     * 远程执行异常
     */
    public static class RemoteExecException extends ClientExecException {
        public RemoteExecException(String message) {
            super(message);
        }
    }

    /**
     * Runtime snapshot 请求异常, 携带稳定错误码 (见开发计划 8.4)
     */
    public static class SnapshotException extends Exception {
        public static final String RUNTIME_OFFLINE = "RUNTIME_OFFLINE";
        public static final String DEVICE_MISMATCH = "DEVICE_MISMATCH";
        public static final String PROJECT_LOCATION_NOT_FOUND = "PROJECT_LOCATION_NOT_FOUND";
        public static final String SNAPSHOT_TIMEOUT = "SNAPSHOT_TIMEOUT";

        private final String code;

        public SnapshotException(String code, String message) {
            super(code + ": " + message);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }
}
