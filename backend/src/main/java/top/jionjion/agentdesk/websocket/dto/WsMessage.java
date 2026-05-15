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
     * 消息类型常量
     */
    public static final String TYPE_COMMAND_REQUEST = "command_request";
    public static final String TYPE_COMMAND_RESULT = "command_result";
    public static final String TYPE_COMMAND_REJECTED = "command_rejected";
    public static final String TYPE_COMMAND_CANCEL = "command_cancel";
    public static final String TYPE_PING = "ping";
    public static final String TYPE_PONG = "pong";
    public static final String TYPE_CONNECTED = "connected";
    public static final String TYPE_CLIENT_READY = "client_ready";

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
