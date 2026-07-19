package top.jionjion.agentdesk.websocket.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * WebSocket 通信消息格式
 *
 * @param type      消息类型
 * @param requestId 请求唯一标识
 * @param sessionId 关联的聊天会话 ID
 * @param timestamp 时间戳
 * @param payload   消息载荷
 * @author Jion
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WsMessage(
        String type,
        String requestId,
        String sessionId,
        long timestamp,
        Map<String, Object> payload
) {

    /**
     * 消息类型常量 (统一本地执行协议, 见开发计划 8.2/8.3)
     */
    public static final String TYPE_COMMAND_REQUEST = "local_exec_request";
    public static final String TYPE_COMMAND_RESULT = "local_exec_result";
    public static final String TYPE_COMMAND_REJECTED = "local_exec_rejected";
    public static final String TYPE_COMMAND_CANCEL = "local_exec_cancel";
    public static final String TYPE_LOCAL_FS_REQUEST = "local_fs_request";
    public static final String TYPE_LOCAL_FS_RESULT = "local_fs_result";
    public static final String TYPE_PING = "ping";
    public static final String TYPE_PONG = "pong";
    public static final String TYPE_CONNECTED = "connected";
    public static final String TYPE_CLIENT_READY = "client_ready";
    public static final String TYPE_RUNTIME_SNAPSHOT_REQUEST = "runtime_snapshot_request";
    public static final String TYPE_RUNTIME_SNAPSHOT_RESULT = "runtime_snapshot_result";

    public static WsMessage of(String type, String requestId, String sessionId, Map<String, Object> payload) {
        return new WsMessage(type, requestId, sessionId, System.currentTimeMillis(), payload);
    }

    public static WsMessage ping() {
        return new WsMessage(TYPE_PING, null, null, System.currentTimeMillis(), null);
    }

    public static WsMessage connected() {
        return new WsMessage(TYPE_CONNECTED, null, null, System.currentTimeMillis(), Map.of("version", "1.0"));
    }

    public static WsMessage cancel(String requestId, String sessionId) {
        return new WsMessage(TYPE_COMMAND_CANCEL, requestId, sessionId, System.currentTimeMillis(), null);
    }
}
