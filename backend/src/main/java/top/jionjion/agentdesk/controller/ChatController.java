package top.jionjion.agentdesk.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import top.jionjion.agentdesk.agent.core.AgentPool;
import top.jionjion.agentdesk.annotation.RateLimit;
import top.jionjion.agentdesk.dto.chat.ChatRequest;
import top.jionjion.agentdesk.dto.chat.SearchResultDto;
import top.jionjion.agentdesk.entity.ChatMessage;
import top.jionjion.agentdesk.repository.SessionRepository;
import top.jionjion.agentdesk.security.UserContext;
import top.jionjion.agentdesk.service.SessionService;
import top.jionjion.agentdesk.service.chat.ChatMessageService;
import top.jionjion.agentdesk.service.chat.ChatStreamOrchestrator;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 对话控制器, 通过SSE流式推送对话事件。
 * <p>
 * 仅负责鉴权、参数校验与委托, 对话流的编排逻辑下沉至 {@link ChatStreamOrchestrator}。
 *
 * @author Jion
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");
    private static final DateTimeFormatter DATETIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final AgentPool agentPool;
    private final SessionRepository sessionRepository;
    private final SessionService sessionService;
    private final ChatMessageService chatMessageService;
    private final ChatStreamOrchestrator chatStreamOrchestrator;

    public ChatController(AgentPool agentPool,
                          SessionRepository sessionRepository, SessionService sessionService,
                          ChatMessageService chatMessageService,
                          ChatStreamOrchestrator chatStreamOrchestrator) {
        this.agentPool = agentPool;
        this.sessionRepository = sessionRepository;
        this.sessionService = sessionService;
        this.chatMessageService = chatMessageService;
        this.chatStreamOrchestrator = chatStreamOrchestrator;
    }

    /**
     * 获取指定会话的聊天记录
     */
    @GetMapping("/messages")
    public List<ChatMessage> getMessages(@RequestParam String sessionId) {
        validateSessionId(sessionId);
        if (!sessionService.belongsToUser(sessionId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该会话");
        }
        return chatMessageService.getHistory(sessionId);
    }

    /**
     * 流式对话接口, 通过SSE推送Agent的回复事件
     */
    @RateLimit(maxRequests = 10, windowSeconds = 70, message = "对话请求过于频繁, 请稍后再试")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody ChatRequest chatRequest) {
        String sessionId = chatRequest.sessionId();
        validateSessionId(sessionId);
        if (chatRequest.message() == null || chatRequest.message().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message is empty");
        }
        if (!sessionService.belongsToUser(sessionId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该会话");
        }
        String resolvedMemoryMode;
        try {
            resolvedMemoryMode = sessionService.resolveMemoryMode(sessionId, chatRequest.memoryMode());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        ChatRequest effectiveRequest = new ChatRequest(chatRequest.sessionId(), chatRequest.message(),
                chatRequest.fileIds(), chatRequest.kbIds(), resolvedMemoryMode, chatRequest.runtimeSnapshot());
        Object lockToken = agentPool.tryAcquire(sessionId);
        if (lockToken == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "session is busy");
        }
        return chatStreamOrchestrator.startChat(effectiveRequest, lockToken);
    }

    /**
     * 重新生成 Assistant 的回复 (SSE 流式)
     */
    @RateLimit(maxRequests = 10, windowSeconds = 70, message = "对话请求过于频繁, 请稍后再试")
    @GetMapping(value = "/regenerate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter regenerate(@RequestParam String sessionId,
                                 @RequestParam Long messageId,
                                 @RequestParam(required = false) String memoryMode) {
        validateSessionId(sessionId);
        if (!sessionService.belongsToUser(sessionId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该会话");
        }

        // 校验目标消息并查找触发该回复的用户消息 (锁前完成, 与原逻辑一致)
        chatMessageService.requireAssistantMessage(sessionId, messageId);
        List<ChatMessage> history = chatMessageService.getHistory(sessionId);
        ChatMessage userMessage = chatMessageService.findTriggeringUserMessageEntity(messageId, history);
        String effectiveMemoryMode = memoryMode == null || memoryMode.isBlank()
                || "INHERIT".equalsIgnoreCase(memoryMode)
                ? userMessage.getMemoryMode() : memoryMode;

        Object lockToken = agentPool.tryAcquire(sessionId);
        if (lockToken == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "session is busy");
        }
        return chatStreamOrchestrator.regenerate(
                sessionId, messageId, userMessage, lockToken, effectiveMemoryMode);
    }

    /**
     * 导出会话聊天记录为 Markdown 文件
     */
    @GetMapping("/export/{sessionId}")
    public ResponseEntity<byte[]> exportMarkdown(@PathVariable String sessionId) {
        validateSessionId(sessionId);
        Long userId = UserContext.getUserId();

        var session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在"));

        List<ChatMessage> messages = chatMessageService.getHistory(sessionId);

        String markdown = buildExportMarkdown(session.getTitle(), messages);
        byte[] content = markdown.getBytes(StandardCharsets.UTF_8);

        String filename = session.getTitle().replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fff_-]", "_") + ".md";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/markdown; charset=UTF-8"))
                .body(content);
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
        List<ChatMessage> messages = chatMessageService.searchByContent(userId, keyword.trim());

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
        if (!sessionService.belongsToUser(sessionId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该会话");
        }
        boolean interrupted = agentPool.interrupt(UserContext.getUserId(), sessionId);
        return Map.of("status", interrupted ? "interrupted" : "not_running");
    }

    private void validateSessionId(String sessionId) {
        if (sessionId == null || !SESSION_ID_PATTERN.matcher(sessionId).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid sessionId");
        }
    }

    private String buildExportMarkdown(String title, List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(title).append("\n\n");
        sb.append("> 导出时间: ").append(DATETIME_FMT.format(Instant.now())).append("\n\n");

        for (ChatMessage msg : messages) {
            if (!ChatMessageService.ROLE_USER.equals(msg.getRole())
                    && !ChatMessageService.ROLE_ASSISTANT.equals(msg.getRole())) {
                continue;
            }
            sb.append("---\n\n");
            String roleName = ChatMessageService.ROLE_USER.equals(msg.getRole()) ? "用户" : "助手";
            String time = DATETIME_FMT.format(Instant.ofEpochMilli(msg.getCreatedAt()));
            sb.append("**").append(roleName).append("** (").append(time).append(")\n\n");
            sb.append(msg.getContent() != null ? msg.getContent() : "").append("\n\n");
        }
        return sb.toString();
    }
}
