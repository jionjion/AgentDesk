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

import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");

    private final AgentPool agentPool;

    public ChatController(AgentPool agentPool) {
        this.agentPool = agentPool;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestParam String sessionId,
                                 @RequestParam String message) {
        if (sessionId == null || !SESSION_ID_PATTERN.matcher(sessionId).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid sessionId");
        }
        if (message == null || message.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message is empty");
        }

        if (!agentPool.tryAcquire(sessionId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "session is busy");
        }

        AgentHandle handle = agentPool.getOrCreate(sessionId);
        SseEmitter emitter = new SseEmitter(300_000L);
        handle.hook().setEmitter(emitter);

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
                        agentPool.save(sessionId);
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

    @PostMapping("/{sessionId}/interrupt")
    public Map<String, String> interrupt(@PathVariable String sessionId) {
        if (!SESSION_ID_PATTERN.matcher(sessionId).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid sessionId");
        }
        AgentHandle handle = agentPool.getOrCreate(sessionId);
        handle.agent().interrupt();
        return Map.of("status", "interrupted");
    }

    private void sendErrorEvent(SseEmitter emitter, String message) {
        try {
            ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String json = mapper.writeValueAsString(ChatEventDto.error(message != null ? message : "unknown error"));
            emitter.send(SseEmitter.event().name("error").data(json));
        } catch (Exception e) {
            log.debug("Failed to send error event: {}", e.getMessage());
        }
    }
}
