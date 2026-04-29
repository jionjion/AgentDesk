package top.jionjion.agentdesk.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import top.jionjion.agentdesk.repository.SessionRepository;
import top.jionjion.agentdesk.security.UserContext;
import top.jionjion.agentdesk.service.FileService;
import top.jionjion.agentdesk.service.TitleGenerationService;
import top.jionjion.agentdesk.session.SessionService;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
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
    private static final Set<String> IMAGE_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );

    private final AgentPool agentPool;
    private final ChatMessageRepository chatMessageRepository;
    private final SessionRepository sessionRepository;
    private final SessionService sessionService;
    private final FileService fileService;
    private final TitleGenerationService titleGenerationService;

    public ChatController(AgentPool agentPool, ChatMessageRepository chatMessageRepository,
                          SessionRepository sessionRepository, SessionService sessionService,
                          FileService fileService, TitleGenerationService titleGenerationService) {
        this.agentPool = agentPool;
        this.chatMessageRepository = chatMessageRepository;
        this.sessionRepository = sessionRepository;
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
        List<FileResponse> imageFiles = files.stream()
                .filter(f -> isImageFile(f.contentType()))
                .toList();
        List<FileResponse> nonImageFiles = files.stream()
                .filter(f -> !isImageFile(f.contentType()))
                .toList();

        // 持久化用户消息 (含 fileIds)
        saveUserMessage(sessionId, message, parsedFileIds);

        configureSseCallbacks(emitter, sessionId, handle);

        // 通知 Hook 长期记忆已启用, 由 Hook 在实际召回时发送事件
        if (handle.longTermMemoryEnabled()) {
            handle.hook().setLongTermMemoryEnabled(true);
        }

        Msg userMsg;
        if (imageFiles.isEmpty()) {
            // 纯文本路径 (无图片附件)
            String enrichedMessage = buildMessageWithFiles(message, nonImageFiles);
            userMsg = Msg.builder()
                    .textContent(enrichedMessage)
                    .build();
        } else {
            // 多模态路径 (含图片附件)
            userMsg = buildMultimodalMsg(message, imageFiles, nonImageFiles);
        }

        handle.agent().stream(userMsg)
                .doOnComplete(() -> onStreamComplete(sessionId, message, handle, emitter))
                .doOnError(e -> {
                    if (handle.hook().isClientDisconnected() || isClientDisconnect(e)) {
                        log.debug("Session {} Agent流处理中客户端已断开", sessionId);
                    } else {
                        log.error("Agent error: {}", e.getMessage(), e);
                    }
                    try {
                        if (!handle.hook().isClientDisconnected()) {
                            sendErrorEvent(emitter, e.getMessage());
                        }
                        emitter.completeWithError(e);
                    } catch (Exception ex) {
                        log.debug("SSE已关闭, 忽略错误事件发送: {}", ex.getMessage());
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
            handle.hook().markDisconnected();
            agentPool.release(sessionId);
        });
        emitter.onCompletion(() -> {
            log.debug("Session {} SSE completed", sessionId);
            handle.hook().setEmitter(null);
        });
        emitter.onError(e -> {
            // 客户端断开连接是正常情况, 降级为 DEBUG
            if (isClientDisconnect(e)) {
                log.debug("Session {} 客户端断开连接", sessionId);
            } else {
                log.warn("Session {} SSE error: {}", sessionId, e.getMessage());
            }
            handle.hook().markDisconnected();
            agentPool.release(sessionId);
        });
    }

    private boolean isClientDisconnect(Throwable e) {
        Throwable cause = e;
        while (cause != null) {
            String msg = cause.getMessage();
            if (msg != null && (msg.contains("Broken pipe")
                    || msg.contains("disconnected client")
                    || msg.contains("Connection reset"))) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private void onStreamComplete(String sessionId, String message, AgentHandle handle, SseEmitter emitter) {
        try {
            String reply = handle.hook().getLastReply();
            if (reply != null && !reply.isEmpty()) {
                ChatMessage saved = chatMessageRepository.save(new ChatMessage(sessionId, "assistant", reply));
                // 发送 message_saved 事件, 携带数据库消息ID, 供前端更新本地ID
                try {
                    String json = OBJECT_MAPPER.writeValueAsString(Map.of("messageId", saved.getId()));
                    emitter.send(SseEmitter.event().name("message_saved").data(json));
                } catch (Exception ex) {
                    log.debug("Failed to send message_saved event: {}", ex.getMessage());
                }
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
     * 导出会话聊天记录为 Markdown 文件
     */
    @GetMapping("/export/{sessionId}")
    public ResponseEntity<byte[]> exportMarkdown(@PathVariable String sessionId) {
        validateSessionId(sessionId);
        Long userId = UserContext.getUserId();

        var session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在"));

        List<ChatMessage> messages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);

        String markdown = buildExportMarkdown(session.getTitle(), messages);
        byte[] content = markdown.getBytes(StandardCharsets.UTF_8);

        String filename = session.getTitle().replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fff_-]", "_") + ".md";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/markdown; charset=UTF-8"))
                .body(content);
    }

    /**
     * 重新生成 Assistant 的回复 (SSE 流式)
     */
    @RateLimit(maxRequests = 10, windowSeconds = 60, message = "对话请求过于频繁, 请稍后再试")
    @GetMapping(value = "/regenerate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter regenerate(@RequestParam String sessionId,
                                 @RequestParam Long messageId) {
        validateSessionId(sessionId);
        if (!sessionService.belongsToUser(sessionId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该会话");
        }

        // 查找目标 assistant 消息
        ChatMessage targetMsg = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "消息不存在"));
        if (!targetMsg.getSessionId().equals(sessionId) || !"assistant".equals(targetMsg.getRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只能重新生成助手消息");
        }

        // 查找触发该回复的用户消息（createdAt 在目标消息之前、最近的一条 user 消息）
        List<ChatMessage> history = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        String userMessage = null;
        for (int i = 0; i < history.size(); i++) {
            if (history.get(i).getId().equals(messageId) && i > 0) {
                // 往前找最近的 user 消息
                for (int j = i - 1; j >= 0; j--) {
                    if ("user".equals(history.get(j).getRole())) {
                        userMessage = history.get(j).getContent();
                        break;
                    }
                }
                break;
            }
        }
        if (userMessage == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未找到对应的用户消息");
        }

        if (!agentPool.tryAcquire(sessionId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "session is busy");
        }

        // 删除旧的 assistant 消息
        chatMessageRepository.deleteById(messageId);

        // 使 agent 失效并重建
        agentPool.invalidate(sessionId);
        AgentHandle handle = agentPool.getOrCreate(sessionId);
        SseEmitter emitter = new SseEmitter(300_000L);
        handle.hook().setEmitter(emitter);

        configureSseCallbacks(emitter, sessionId, handle);

        // 通知 Hook 长期记忆已启用, 由 Hook 在实际召回时发送事件
        if (handle.longTermMemoryEnabled()) {
            handle.hook().setLongTermMemoryEnabled(true);
        }

        String finalUserMessage = userMessage;
        Msg userMsg = Msg.builder().textContent(finalUserMessage).build();

        handle.agent().stream(userMsg)
                .doOnComplete(() -> onStreamComplete(sessionId, finalUserMessage, handle, emitter))
                .doOnError(e -> {
                    if (handle.hook().isClientDisconnected() || isClientDisconnect(e)) {
                        log.debug("Session {} Agent流处理中客户端已断开", sessionId);
                    } else {
                        log.error("Regenerate error: {}", e.getMessage(), e);
                    }
                    try {
                        if (!handle.hook().isClientDisconnected()) {
                            sendErrorEvent(emitter, e.getMessage());
                        }
                        emitter.completeWithError(e);
                    } catch (Exception ex) {
                        log.debug("SSE已关闭, 忽略错误事件发送: {}", ex.getMessage());
                    }
                })
                .doFinally(signal -> agentPool.release(sessionId))
                .subscribe();

        return emitter;
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

    /**
     * 向客户端发送错误事件
     */
    private void sendErrorEvent(SseEmitter emitter, String errorMessage) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(ChatEventDto.error(errorMessage != null ? errorMessage : "unknown error"));
            emitter.send(SseEmitter.event().name("error").data(json));
        } catch (Exception e) {
            log.debug("Failed to send error event: {}", e.getMessage());
        }
    }

    /**
     * 解析逗号分隔的 fileIds 字符串
     */
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

    /**
     * 将文件元信息拼入用户消息
     */
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

    /**
     * 判断是否为图片文件
     */
    private boolean isImageFile(String contentType) {
        return contentType != null && IMAGE_CONTENT_TYPES.contains(contentType.toLowerCase());
    }

    /**
     * 构建多模态消息 (含图片 ImageBlock)
     */
    private Msg buildMultimodalMsg(String message, List<FileResponse> imageFiles, List<FileResponse> nonImageFiles) {
        List<ContentBlock> blocks = new ArrayList<>();

        // 文本块: 用户消息 + 非图片文件描述
        String textPart = buildMessageWithFiles(message, nonImageFiles);
        blocks.add(TextBlock.builder().text(textPart).build());

        // 图片块: 使用 OSS 预签名 URL
        for (FileResponse img : imageFiles) {
            blocks.add(ImageBlock.builder()
                    .source(URLSource.builder()
                            .url(img.downloadUrl())
                            .build())
                    .build());
        }

        return Msg.builder()
                .role(MsgRole.USER)
                .content(blocks)
                .build();
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

    private static final DateTimeFormatter DATETIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private String buildExportMarkdown(String title, List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(title).append("\n\n");
        sb.append("> 导出时间: ").append(DATETIME_FMT.format(Instant.now())).append("\n\n");

        for (ChatMessage msg : messages) {
            if (!"user".equals(msg.getRole()) && !"assistant".equals(msg.getRole())) {
                continue;
            }
            sb.append("---\n\n");
            String roleName = "user".equals(msg.getRole()) ? "用户" : "助手";
            String time = DATETIME_FMT.format(Instant.ofEpochMilli(msg.getCreatedAt()));
            sb.append("**").append(roleName).append("** (").append(time).append(")\n\n");
            sb.append(msg.getContent() != null ? msg.getContent() : "").append("\n\n");
        }
        return sb.toString();
    }
}
