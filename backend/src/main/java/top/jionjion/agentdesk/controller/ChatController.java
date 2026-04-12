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
import top.jionjion.agentdesk.annotation.RateLimit;
import top.jionjion.agentdesk.dto.ChatEventDto;
import top.jionjion.agentdesk.dto.FileResponse;
import top.jionjion.agentdesk.dto.SearchResultDto;
import top.jionjion.agentdesk.entity.ChatMessage;
import top.jionjion.agentdesk.repository.ChatMessageRepository;
import top.jionjion.agentdesk.security.UserContext;
import top.jionjion.agentdesk.service.FileService;
import top.jionjion.agentdesk.service.TitleGenerationService;
import top.jionjion.agentdesk.session.SessionService;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 对话控制器, 通过SSE流式推送对话事件
 *
 * @author Jion
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int BYTES_PER_KB = 1024;
    private static final int BYTES_PER_MB = 1024 * 1024;
    private static final double KB_DIVISOR = 1024.0;
    private static final String FORMAT_KB = "%.1fKB";
    private static final String FORMAT_MB = "%.1fMB";
    private static final String UNIT_BYTE = "B";

    private final AgentPool agentPool;
    private final ChatMessageRepository chatMessageRepository;
    private final SessionService sessionService;
    private final FileService fileService;
    private final TitleGenerationService titleGenerationService;

    public ChatController(AgentPool agentPool, ChatMessageRepository chatMessageRepository,
                          SessionService sessionService, FileService fileService,
                          TitleGenerationService titleGenerationService) {
        this.agentPool = agentPool;
        this.chatMessageRepository = chatMessageRepository;
        this.sessionService = sessionService;
        this.fileService = fileService;
        this.titleGenerationService = titleGenerationService;
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
    @RateLimit(maxRequests = 10, windowSeconds = 60, message = "对话请求过于频繁, 请稍后再试")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestParam String sessionId,
                                 @RequestParam String message,
                                 @RequestParam(required = false) String fileIds) {
        validateSessionId(sessionId);
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

        // 解析 fileIds 并查询文件元数据
        List<Long> parsedFileIds = parseFileIds(fileIds);
        List<FileResponse> files = parsedFileIds.isEmpty()
                ? Collections.emptyList()
                : fileService.getByIds(parsedFileIds);

        // 拼接带文件信息的 prompt
        String enrichedMessage = buildMessageWithFiles(message, files);

        // 持久化用户消息 (含 fileIds)
        saveUserMessage(sessionId, message, parsedFileIds);

        configureSseCallbacks(emitter, sessionId, handle);

        Msg userMsg = Msg.builder()
                .textContent(enrichedMessage)
                .build();

        handle.agent().stream(userMsg)
                .doOnComplete(() -> onStreamComplete(sessionId, message, handle, emitter))
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

    private void validateSessionId(String sessionId) {
        if (sessionId == null || !SESSION_ID_PATTERN.matcher(sessionId).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid sessionId");
        }
    }

    private void saveUserMessage(String sessionId, String message, List<Long> parsedFileIds) {
        ChatMessage chatMsg = new ChatMessage(sessionId, "user", message);
        if (!parsedFileIds.isEmpty()) {
            chatMsg.setFileIds(parsedFileIds);
        }
        chatMessageRepository.save(chatMsg);
    }

    private void configureSseCallbacks(SseEmitter emitter, String sessionId, AgentHandle handle) {
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
    }

    private void onStreamComplete(String sessionId, String message, AgentHandle handle, SseEmitter emitter) {
        try {
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

        // 首次对话时异步生成标题 (emitter 已关闭, 不再通过 SSE 推送)
        if (sessionService.hasDefaultTitle(sessionId)) {
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    String generatedTitle = titleGenerationService.generateTitle(message);
                    if (generatedTitle != null && !generatedTitle.isEmpty()) {
                        sessionService.updateTitleInternal(sessionId, generatedTitle);
                    }
                } catch (Exception e) {
                    log.warn("自动生成标题失败: {}", e.getMessage());
                }
            });
        }
    }

    /**
     * 全文搜索消息
     */
    @GetMapping("/search")
    public List<SearchResultDto> searchMessages(@RequestParam String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Collections.emptyList();
        }
        Long userId = UserContext.getUserId();
        Map<String, String> titleMap = sessionService.getSessionTitleMap();
        List<ChatMessage> messages = chatMessageRepository.searchByContent(userId, keyword.trim());

        return messages.stream()
                .map(m -> new SearchResultDto(
                        m.getId(),
                        m.getSessionId(),
                        titleMap.getOrDefault(m.getSessionId(), "未知会话"),
                        m.getRole(),
                        m.getContent(),
                        m.getCreatedAt()
                ))
                .toList();
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

    /** 解析逗号分隔的 fileIds 字符串 */
    private List<Long> parseFileIds(String fileIds) {
        if (fileIds == null || fileIds.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(fileIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .toList();
    }

    /** 将文件元信息拼入用户消息 */
    private String buildMessageWithFiles(String message, List<FileResponse> files) {
        if (files.isEmpty()) {
            return message;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[用户上传了以下文件]\n");
        for (FileResponse f : files) {
            sb.append(String.format("- %s (大小: %s, 类型: %s, fileId: %d)\n",
                    f.originalName(),
                    formatSize(f.size()),
                    f.contentType(),
                    f.id()));
        }
        sb.append("\n[用户消息]\n");
        sb.append(message);
        return sb.toString();
    }

    private String formatSize(long bytes) {
        if (bytes < BYTES_PER_KB) {
            return bytes + UNIT_BYTE;
        }
        if (bytes < BYTES_PER_MB) {
            return String.format(FORMAT_KB, bytes / KB_DIVISOR);
        }
        return String.format(FORMAT_MB, bytes / (KB_DIVISOR * BYTES_PER_KB));
    }
}
