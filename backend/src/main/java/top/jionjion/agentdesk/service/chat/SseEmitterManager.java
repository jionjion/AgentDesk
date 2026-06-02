package top.jionjion.agentdesk.service.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import top.jionjion.agentdesk.agent.core.AgentHandle;
import top.jionjion.agentdesk.dto.chat.ChatEventDto;
import top.jionjion.agentdesk.dto.knowledge.RetrievalResultDto;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * SSE 基础设施: 创建 emitter、配置回调与心跳、发送各类事件、识别客户端断开。
 * <p>
 * 不依赖 AgentPool 等业务组件, 会话释放逻辑由调用方通过 onRelease 回调注入。
 *
 * @author Jion
 */
@Component
public class SseEmitterManager {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterManager.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final long HEARTBEAT_INTERVAL_SECONDS = 30;
    private static final long DEFAULT_TIMEOUT_MS = 300_000L;

    private static final Set<String> DISCONNECT_KEYWORDS = Set.of(
            "Broken pipe", "broken pipe", "disconnected client",
            "Connection reset", "connection reset", "中止"
    );

    /**
     * SSE 心跳调度器, 定期发送事件防止连接被中间网络设备断开
     */
    private static final ScheduledExecutorService HEARTBEAT_SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sse-heartbeat");
        t.setDaemon(true);
        return t;
    });

    /**
     * 创建默认超时的 SseEmitter
     */
    public SseEmitter create() {
        return new SseEmitter(DEFAULT_TIMEOUT_MS);
    }

    /**
     * 配置 SSE 生命周期回调与心跳。会话释放交由 onRelease 回调执行, 避免反向依赖 AgentPool。
     */
    public void configureCallbacks(SseEmitter emitter, String sessionId, AgentHandle handle, Runnable onRelease) {
        ScheduledFuture<?> heartbeat = startHeartbeat(emitter, sessionId);
        emitter.onTimeout(() -> {
            log.warn("Session {} SSE timeout", sessionId);
            heartbeat.cancel(false);
            handle.hook().markDisconnected();
            onRelease.run();
        });
        emitter.onCompletion(() -> {
            log.debug("Session {} SSE completed", sessionId);
            heartbeat.cancel(false);
            handle.hook().setEmitter(null);
        });
        emitter.onError(e -> {
            heartbeat.cancel(false);
            // 客户端断开连接是正常情况, 降级为 DEBUG
            if (isClientDisconnect(e)) {
                log.debug("Session {} 客户端断开连接", sessionId);
            } else {
                log.warn("Session {} SSE error: {}", sessionId, e.getMessage());
            }
            handle.hook().markDisconnected();
            onRelease.run();
        });
    }

    /**
     * 启动 SSE 心跳, 每 30 秒发送一个心跳事件保持连接活跃
     */
    private ScheduledFuture<?> startHeartbeat(SseEmitter emitter, String sessionId) {
        return HEARTBEAT_SCHEDULER.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().name("heartbeat").data(""));
            } catch (Exception e) {
                log.debug("Session {} 心跳发送失败, 连接可能已关闭: {}", sessionId, e.getMessage());
            }
        }, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 发送知识库检索结果事件
     */
    public void sendKnowledgeRetrieved(SseEmitter emitter, List<RetrievalResultDto> retrievalResults) {
        if (retrievalResults.isEmpty()) {
            return;
        }
        try {
            List<Map<String, Object>> refs = retrievalResults.stream()
                    .map(r -> Map.<String, Object>of(
                            "documentName", r.documentName(),
                            "score", Math.round(r.score() * 100) / 100.0,
                            "chunkIndex", r.chunkIndex()
                    )).toList();
            List<String> sources = retrievalResults.stream()
                    .map(RetrievalResultDto::documentName)
                    .distinct().toList();
            String json = OBJECT_MAPPER.writeValueAsString(Map.of(
                    "count", retrievalResults.size(),
                    "sources", sources,
                    "references", refs
            ));
            emitter.send(SseEmitter.event().name("knowledge_retrieved").data(json));
        } catch (Exception ex) {
            log.debug("Failed to send knowledge_retrieved event: {}", ex.getMessage());
        }
    }

    /**
     * 发送消息已持久化事件, 携带数据库消息ID
     */
    public void sendMessageSaved(SseEmitter emitter, Long messageId) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(Map.of("messageId", messageId));
            emitter.send(SseEmitter.event().name("message_saved").data(json));
        } catch (Exception ex) {
            log.debug("Failed to send message_saved event: {}", ex.getMessage());
        }
    }

    /**
     * 向客户端发送错误事件
     */
    public void sendError(SseEmitter emitter, String errorMessage) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(
                    ChatEventDto.error(errorMessage != null ? errorMessage : "unknown error"));
            emitter.send(SseEmitter.event().name("error").data(json));
        } catch (Exception e) {
            log.debug("Failed to send error event: {}", e.getMessage());
        }
    }

    /**
     * 判断异常链中是否包含客户端断开的特征
     */
    public boolean isClientDisconnect(Throwable e) {
        Throwable cause = e;
        while (cause != null) {
            String msg = cause.getMessage();
            if (msg != null && containsDisconnectKeyword(msg)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private boolean containsDisconnectKeyword(String msg) {
        for (String keyword : DISCONNECT_KEYWORDS) {
            if (msg.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
