package top.jionjion.agentdesk.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import top.jionjion.agentdesk.websocket.dto.CommandResult;
import top.jionjion.agentdesk.websocket.dto.WsMessage;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 远程执行 WebSocket 处理器: 管理连接生命周期和消息路由。
 *
 * @author Jion
 */
@Component
public class RemoteExecWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(RemoteExecWebSocketHandler.class);
    private static final long HEARTBEAT_INTERVAL = 30_000L;
    private static final long HEARTBEAT_TIMEOUT = 10_000L;

    private final RemoteExecBridge bridge;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService heartbeatScheduler = Executors.newScheduledThreadPool(1);

    public RemoteExecWebSocketHandler(RemoteExecBridge bridge, ObjectMapper objectMapper) {
        this.bridge = bridge;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
        Long userId = getUserId(session);
        if (userId == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        // 注册连接
        bridge.registerConnection(userId, session);

        // 发送连接确认
        sendMessage(session, WsMessage.connected());

        // 启动心跳
        startHeartbeat(session, userId);
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) throws Exception {
        Long userId = getUserId(session);
        if (userId == null) {
            return;
        }

        WsMessage wsMsg;
        try {
            wsMsg = objectMapper.readValue(message.getPayload(), WsMessage.class);
        } catch (Exception e) {
            log.warn("解析 WebSocket 消息失败: {}", e.getMessage());
            return;
        }

        switch (wsMsg.type()) {
            case WsMessage.TYPE_PONG -> {
                // 心跳响应, 更新最后活跃时间
                session.getAttributes().put("lastPong", System.currentTimeMillis());
            }
            case WsMessage.TYPE_COMMAND_RESULT -> handleCommandResult(wsMsg);
            case WsMessage.TYPE_COMMAND_REJECTED -> handleCommandRejected(wsMsg);
            case WsMessage.TYPE_CLIENT_READY -> {
                log.info("用户 {} 客户端就绪, payload={}", userId, wsMsg.payload());
                // 提取并存储客户端平台信息
                if (wsMsg.payload() != null) {
                    String platform = (String) wsMsg.payload().get("platform");
                    bridge.registerClientPlatform(userId, platform);
                }
            }
            default -> log.warn("未知消息类型: {}", wsMsg.type());
        }
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        Long userId = getUserId(session);
        if (userId != null) {
            bridge.removeConnection(userId);
            cancelHeartbeat(session);
        }
        log.info("WebSocket 连接关闭: userId={}, status={}", userId, status);
    }

    @Override
    public void handleTransportError(@NonNull WebSocketSession session, @NonNull Throwable exception) {
        Long userId = getUserId(session);
        log.warn("WebSocket 传输错误: userId={}, error={}", userId, exception.getMessage());
        if (userId != null) {
            bridge.removeConnection(userId);
            cancelHeartbeat(session);
        }
    }

    // ==================== 消息处理 ====================

    private void handleCommandResult(WsMessage msg) {
        Map<String, Object> payload = msg.payload();
        if (payload == null || msg.requestId() == null) {
            return;
        }

        int exitCode = ((Number) payload.getOrDefault("exitCode", -1)).intValue();
        String stdout = (String) payload.getOrDefault("stdout", "");
        String stderr = (String) payload.getOrDefault("stderr", "");
        long durationMs = ((Number) payload.getOrDefault("durationMs", 0)).longValue();

        CommandResult result = new CommandResult(exitCode, stdout, stderr, durationMs);
        bridge.onCommandResult(msg.requestId(), result);
    }

    private void handleCommandRejected(WsMessage msg) {
        if (msg.requestId() == null) {
            return;
        }
        Map<String, Object> payload = msg.payload();
        String reason = payload != null ? (String) payload.getOrDefault("reason", "") : "";
        bridge.onCommandRejected(msg.requestId(), reason);
    }

    // ==================== 心跳管理 ====================

    private void startHeartbeat(WebSocketSession session, Long userId) {
        session.getAttributes().put("lastPong", System.currentTimeMillis());
        ScheduledFuture<?> heartbeatTask = heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                if (!session.isOpen()) {
                    cancelHeartbeat(session);
                    return;
                }

                // 检查上次 pong 是否超时
                Long lastPong = (Long) session.getAttributes().get("lastPong");
                if (lastPong != null && System.currentTimeMillis() - lastPong > HEARTBEAT_INTERVAL + HEARTBEAT_TIMEOUT) {
                    log.warn("用户 {} 心跳超时, 关闭连接", userId);
                    session.close(CloseStatus.GOING_AWAY);
                    return;
                }

                sendMessage(session, WsMessage.ping());
            } catch (Exception e) {
                log.warn("心跳发送失败: {}", e.getMessage());
            }
        }, HEARTBEAT_INTERVAL, HEARTBEAT_INTERVAL, TimeUnit.MILLISECONDS);

        session.getAttributes().put("heartbeatTask", heartbeatTask);
    }

    @SuppressWarnings("unchecked")
    private void cancelHeartbeat(WebSocketSession session) {
        Object task = session.getAttributes().get("heartbeatTask");
        if (task instanceof ScheduledFuture<?> future) {
            future.cancel(false);
        }
    }

    // ==================== 工具方法 ====================

    private Long getUserId(WebSocketSession session) {
        return (Long) session.getAttributes().get(JwtHandshakeInterceptor.ATTR_USER_ID);
    }

    private void sendMessage(WebSocketSession session, WsMessage msg) {
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(msg)));
        } catch (IOException e) {
            log.warn("发送 WebSocket 消息失败: {}", e.getMessage());
        }
    }
}
