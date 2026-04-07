package top.jionjion.agentdesk.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.Msg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import top.jionjion.agentdesk.agent.AgentHandle;
import top.jionjion.agentdesk.agent.AgentPool;
import top.jionjion.agentdesk.dto.ChatEventDto;
import top.jionjion.agentdesk.entity.ChatMessage;
import top.jionjion.agentdesk.repository.ChatMessageRepository;
import top.jionjion.agentdesk.session.SessionService;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 对话控制器, 通过SSE流式推送对话事件
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AgentPool agentPool;
    private final ChatMessageRepository chatMessageRepository;
    private final SessionService sessionService;

    public ChatController(AgentPool agentPool, ChatMessageRepository chatMessageRepository, SessionService sessionService) {
        this.agentPool = agentPool;
        this.chatMessageRepository = chatMessageRepository;
        this.sessionService = sessionService;
    }

    /**
     * 获取指定会话的聊天记录
     */
    @GetMapping("/messages")
    public List<ChatMessage> getMessages(@RequestParam String sessionId) {
        if (sessionId == null || !SESSION_ID_PATTERN.matcher(sessionId).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid sessionId");
        }
        if (!sessionService.belongsToUser(sessionId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该会话");
        }
        return chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    /**
     * 流式对话接口, 通过SSE推送Agent的回复事件
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestParam String sessionId,
                                 @RequestParam String message) {
        if (sessionId == null || !SESSION_ID_PATTERN.matcher(sessionId).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid sessionId");
        }
        if (message == null || message.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message is empty");
        }
        if (!sessionService.belongsToUser(sessionId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该会话");
        }

        if (!agentPool.tryAcquire(sessionId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "session is busy");
        }

        AgentHandle handle = agentPool.getOrCreate(sessionId);
        SseEmitter emitter = new SseEmitter(300_000L);
        handle.hook().setEmitter(emitter);

        // 持久化用户消息
        chatMessageRepository.save(new ChatMessage(sessionId, "user", message));

        emitter.onTimeout(() -> {
            log.warn("Session {} SSE timeout", sessionId);
            agentPool.release(sessionId);
        });
        emitter.onCompletion(() -> {
            log.debug("Session {} SSE completed", sessionId);
            handle.hook().setEmitter(null);
        });
        emitter.onError(e -> {
            log.warn("Session {} SSE error: {}", sessionId, e.getMessage());
            agentPool.release(sessionId);
            handle.hook().setEmitter(null);
        });

        Msg userMsg = Msg.builder()
                .textContent(message)
                .build();

        handle.agent().stream(userMsg)
                .doOnComplete(() -> {
                    try {
                        // 持久化 Agent 回复: 从 Hook 的 PostCallEvent 中获取最终文本
                        String reply = handle.hook().getLastReply();
                        if (reply != null && !reply.isEmpty()) {
                            chatMessageRepository.save(new ChatMessage(sessionId, "assistant", reply));
                        }
                        agentPool.save(sessionId);
                        sessionService.touch(sessionId);
                        emitter.complete();
                    } catch (Exception e) {
                        log.warn("Error completing SSE: {}", e.getMessage());
                    }
                })
                .doOnError(e -> {
                    log.error("Agent error: {}", e.getMessage(), e);
                    try {
                        sendErrorEvent(emitter, e.getMessage());
                        emitter.completeWithError(e);
                    } catch (Exception ex) {
                        log.warn("Error sending error event: {}", ex.getMessage());
                    }
                })
                .doFinally(signal -> agentPool.release(sessionId))
                .subscribe();

        return emitter;
    }

    /**
     * 中断当前会话的Agent执行
     */
    @PostMapping("/{sessionId}/interrupt")
    public Map<String, String> interrupt(@PathVariable String sessionId) {
        if (!SESSION_ID_PATTERN.matcher(sessionId).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid sessionId");
        }
        AgentHandle handle = agentPool.getOrCreate(sessionId);
        handle.agent().interrupt();
        return Map.of("status", "interrupted");
    }

    /** 向客户端发送错误事件 */
    private void sendErrorEvent(SseEmitter emitter, String message) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(ChatEventDto.error(message != null ? message : "unknown error"));
            emitter.send(SseEmitter.event().name("error").data(json));
        } catch (Exception e) {
            log.debug("Failed to send error event: {}", e.getMessage());
        }
    }
}
