package top.jionjion.agentdesk.service.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import top.jionjion.agentdesk.agent.core.AgentHandle;
import top.jionjion.agentdesk.agent.core.AgentPool;
import top.jionjion.agentdesk.agent.runtime.AgentInput;
import top.jionjion.agentdesk.agent.runtime.AgentRunContext;
import top.jionjion.agentdesk.agent.runtime.ProjectRuntimeContext;
import top.jionjion.agentdesk.dto.chat.ChatRequest;
import top.jionjion.agentdesk.dto.file.FileResponse;
import top.jionjion.agentdesk.dto.knowledge.RetrievalResultDto;
import top.jionjion.agentdesk.dto.memory.MemoryRecallResult;
import top.jionjion.agentdesk.entity.ChatMessage;
import top.jionjion.agentdesk.security.UserContext;
import top.jionjion.agentdesk.service.FileService;
import top.jionjion.agentdesk.service.KnowledgeRetrievalService;
import top.jionjion.agentdesk.service.MemoryService;
import top.jionjion.agentdesk.service.RetrievalIntentService;
import top.jionjion.agentdesk.service.memory.MemoryJobService;

import java.util.Collections;
import java.util.List;

/**
 * 对话流编排: 协调文件解析、消息持久化、知识检索、prompt 组装、SSE 推送与 Agent 流订阅。
 * <p>
 * 是 {@code stream} 与 {@code regenerate} 两个端点的公共流程承载者, ChatController 仅负责
 * 鉴权与参数校验后委托至此。
 *
 * @author Jion
 */
@Service
public class ChatStreamOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ChatStreamOrchestrator.class);
    private final AgentPool agentPool;
    private final FileService fileService;
    private final ChatMessageService chatMessageService;
    private final PromptContextBuilder promptContextBuilder;
    private final ProjectRuntimeContextResolver projectContextResolver;
    private final SseEmitterManager sseEmitterManager;
    private final KnowledgeRetrievalService knowledgeRetrievalService;
    private final RetrievalIntentService retrievalIntentService;
    private final MemoryService memoryService;
    private final MemoryJobService memoryJobService;

    public ChatStreamOrchestrator(AgentPool agentPool,
                                  FileService fileService,
                                  ChatMessageService chatMessageService,
                                  PromptContextBuilder promptContextBuilder,
                                  ProjectRuntimeContextResolver projectContextResolver,
                                  SseEmitterManager sseEmitterManager,
                                  KnowledgeRetrievalService knowledgeRetrievalService,
                                  RetrievalIntentService retrievalIntentService,
                                  MemoryService memoryService,
                                  MemoryJobService memoryJobService) {
        this.agentPool = agentPool;
        this.fileService = fileService;
        this.chatMessageService = chatMessageService;
        this.promptContextBuilder = promptContextBuilder;
        this.projectContextResolver = projectContextResolver;
        this.sseEmitterManager = sseEmitterManager;
        this.knowledgeRetrievalService = knowledgeRetrievalService;
        this.retrievalIntentService = retrievalIntentService;
        this.memoryService = memoryService;
        this.memoryJobService = memoryJobService;
    }

    /**
     * 启动一次新对话流。调用方需已通过鉴权且已 tryAcquire 成功获取会话锁, 并传入对应 token。
     */
    public SseEmitter startChat(ChatRequest chatRequest, Object lockToken) {
        String sessionId = chatRequest.sessionId();
        String message = chatRequest.message();
        try {
            String memoryMode = normalizeMemoryMode(chatRequest.memoryMode());
            Long userId = UserContext.getUserId();
            // 本轮不允许读取长期记忆时, 确保 Agent 状态中没有历史轮次注入的记忆文本
            AgentHandle handle = prepareHandle(userId, sessionId, memoryMode);
            SseEmitter emitter = sseEmitterManager.create();
            handle.attachEmitter(emitter);

            // 解析文件并持久化用户消息
            List<Long> parsedFileIds = promptContextBuilder.parseFileIds(chatRequest.fileIds());
            List<FileResponse> files = parsedFileIds.isEmpty()
                    ? Collections.emptyList()
                    : fileService.getByIds(parsedFileIds);
            List<FileResponse> imageFiles = files.stream()
                    .filter(f -> promptContextBuilder.isImageFile(f.contentType())).toList();
            List<FileResponse> nonImageFiles = files.stream()
                    .filter(f -> !promptContextBuilder.isImageFile(f.contentType())).toList();
            ChatMessage savedUserMessage = chatMessageService.saveUserMessage(
                    sessionId, message, parsedFileIds, memoryMode);

            // 解析会话绑定项目 + 客户端 snapshot -> 调用级项目上下文 (未绑定项目时为 null)
            ProjectRuntimeContext projectContext = projectContextResolver.resolve(
                    userId, sessionId, chatRequest.runtimeSnapshot());
            String projectId = projectContext == null ? null : projectContext.projectId();
            MemoryRecallResult memory = performMemoryRecall(
                    userId, projectId, message, memoryMode);

            // 知识库检索增强
            RetrievalContext retrieval = performKnowledgeRetrieval(message, chatRequest.kbIds());
            String memoryAugmented = promptContextBuilder.buildMemoryAugmentedMessage(
                    retrieval.augmentedMessage(), memory.items());
            if (!memory.items().isEmpty()) {
                handle.markMemoryFreeState(false);
            }

            // 项目上下文增强 (最外层, 模型每轮可见)
            String augmentedMessage = promptContextBuilder.buildProjectAugmentedMessage(
                    memoryAugmented, projectContext);

            sseEmitterManager.configureCallbacks(emitter, sessionId, handle, () -> agentPool.release(sessionId, lockToken));
            sseEmitterManager.sendKnowledgeRetrieved(emitter, retrieval.results());
            sseEmitterManager.sendMemoryRecallCompleted(emitter, memory);
            // 构建用户消息并启动 Agent 流
            AgentInput input = promptContextBuilder.buildAgentInput(augmentedMessage, imageFiles, nonImageFiles);
            AgentRunContext runContext = AgentRunContext.of(
                    userId, sessionId, projectContext, memoryMode, savedUserMessage.getId());
            subscribe(handle, input, runContext, sessionId, message, emitter, lockToken,
                    userId, savedUserMessage.getId(), projectId, memory,
                    memoryMode);

            return emitter;
        } catch (RuntimeException e) {
            // 订阅前任何异常都需释放会话锁, 避免锁泄漏
            agentPool.release(sessionId, lockToken);
            throw e;
        }
    }

    /**
     * 重新生成指定 assistant 消息。调用方需已通过鉴权且已 tryAcquire 成功获取会话锁, 并传入对应 token。
     */
    public SseEmitter regenerate(String sessionId, Long messageId,
                                 ChatMessage userMessage, Object lockToken, String memoryMode) {
        try {
            String effectiveMemoryMode = normalizeMemoryMode(memoryMode);
            // 重生成是一次分支回滚：删除目标回复及其后的消息，同时将 v2 AgentState
            // 重建到触发用户消息之前，避免旧回复、工具结果或后续对话污染新答案。
            List<ChatMessage> historyBeforeTurn = chatMessageService.getHistoryBefore(
                    sessionId, userMessage.getId());
            Long userId = UserContext.getUserId();
            memoryJobService.cancelUserMessages(userId,
                    chatMessageService.getBranchUserMessageIds(sessionId, messageId));
            chatMessageService.deleteBranchFrom(sessionId, messageId, userId);

            agentPool.resetState(userId, sessionId);
            AgentHandle handle = agentPool.getOrCreate(sessionId);
            handle.restoreHistory(userId, sessionId, historyBeforeTurn);
            handle.markMemoryFreeState(true);
            SseEmitter emitter = sseEmitterManager.create();
            handle.attachEmitter(emitter);

            sseEmitterManager.configureCallbacks(emitter, sessionId, handle, () -> agentPool.release(sessionId, lockToken));
            // 重新生成读取 Session 当前绑定的 Project (GET 端点无 snapshot, 运行环境标记为离线; 未绑定项目时为 null)
            ProjectRuntimeContext projectContext = projectContextResolver.resolve(userId, sessionId, null);
            String projectId = projectContext == null ? null : projectContext.projectId();
            MemoryRecallResult memory = performMemoryRecall(
                    userId, projectId, userMessage.getContent(), effectiveMemoryMode);
            String memoryAugmented = promptContextBuilder.buildMemoryAugmentedMessage(
                    userMessage.getContent(), memory.items());
            if (!memory.items().isEmpty()) {
                handle.markMemoryFreeState(false);
            }
            String augmentedMessage = promptContextBuilder.buildProjectAugmentedMessage(
                    memoryAugmented, projectContext);
            sseEmitterManager.sendMemoryRecallCompleted(emitter, memory);
            AgentInput input = AgentInput.text(augmentedMessage);
            AgentRunContext runContext = AgentRunContext.of(
                    userId, sessionId, projectContext, effectiveMemoryMode, userMessage.getId());
            subscribe(handle, input, runContext, sessionId, userMessage.getContent(), emitter,
                    lockToken, userId, userMessage.getId(), projectId, memory,
                    effectiveMemoryMode);

            return emitter;
        } catch (RuntimeException e) {
            agentPool.release(sessionId, lockToken);
            throw e;
        }
    }

    /**
     * 订阅 Agent 流, 处理完成、错误与释放
     */
    private void subscribe(AgentHandle handle, AgentInput input, AgentRunContext runContext,
                           String sessionId, String triggeringMessage, SseEmitter emitter,
                           Object lockToken, Long userId, Long userMessageId, String projectId,
                           MemoryRecallResult memoryRecall, String memoryMode) {
        handle.stream(input, runContext)
                .doOnComplete(() -> onStreamComplete(sessionId, triggeringMessage, handle, emitter,
                        userId, userMessageId, projectId, memoryRecall, memoryMode))
                .doOnError(e -> {
                    if (handle.isClientDisconnected() || sseEmitterManager.isClientDisconnect(e)) {
                        log.debug("Session {} Agent流处理中客户端已断开", sessionId);
                    } else {
                        log.error("Agent error: {}", e.getMessage(), e);
                    }
                    try {
                        if (!handle.isClientDisconnected()) {
                            sseEmitterManager.sendError(emitter, e.getMessage());
                        }
                        emitter.completeWithError(e);
                    } catch (Exception ex) {
                        log.debug("SSE已关闭, 忽略错误事件发送: {}", ex.getMessage());
                    }
                })
                .doFinally(signal -> agentPool.release(sessionId, lockToken))
                .subscribe();
    }

    /**
     * Agent 流完成: 持久化回复、发送 message_saved、保存会话状态、收尾
     */
    private void onStreamComplete(String sessionId, String triggeringMessage,
                                  AgentHandle handle, SseEmitter emitter,
                                  Long userId, Long userMessageId, String projectId,
                                  MemoryRecallResult memoryRecall, String memoryMode) {
        try {
            String reply = handle.lastReply();
            boolean queueMemory = shouldQueueMemory(userId, projectId, memoryMode, memoryRecall);
            Long savedId = chatMessageService.saveAssistantReplyAndQueueMemory(
                    sessionId, reply, memoryRecall, queueMemory, userId,
                    projectId, userMessageId, triggeringMessage, memoryMode);
            if (savedId != null && !handle.isClientDisconnected()) {
                sseEmitterManager.sendMessageSaved(emitter, savedId);
            }
            chatMessageService.finalizeSession(sessionId, triggeringMessage);
            if (!handle.isClientDisconnected()) {
                emitter.complete();
            }
        } catch (Exception e) {
            if (handle.isClientDisconnected() || sseEmitterManager.isClientDisconnect(e)) {
                log.debug("Session {} SSE complete 时客户端已断开: {}", sessionId, e.getMessage());
                return;
            }
            log.error("Session {} 保存助手回复或记忆 outbox 失败", sessionId, e);
            try {
                sseEmitterManager.sendError(emitter, "回复保存失败，请重试");
                emitter.completeWithError(e);
            } catch (Exception sendError) {
                log.debug("Session {} 无法发送保存失败事件: {}", sessionId, sendError.getMessage());
            }
        }
    }

    private boolean shouldQueueMemory(Long userId, String projectId,
                                      String memoryMode, MemoryRecallResult memoryRecall) {
        try {
            return !"NO_MEMORY".equalsIgnoreCase(memoryMode)
                    && !"DISABLED".equals(memoryRecall.status())
                    && memoryService.shouldAutoLearn(userId, projectId);
        } catch (RuntimeException ex) {
            log.warn("Unable to evaluate memory queue policy for userId={}: {}",
                    userId, ex.getClass().getSimpleName());
            return false;
        }
    }

    private record RetrievalContext(String augmentedMessage, List<RetrievalResultDto> results) {
    }

    private MemoryRecallResult performMemoryRecall(Long userId, String projectId,
                                                    String message, String memoryMode) {
        return memoryService.recall(userId, projectId, message, memoryMode);
    }

    /**
     * 获取会话 Agent。本轮为 NO_MEMORY 或用户已关闭长期记忆时, 若状态尚未确认干净,
     * 则从原始消息重建 AgentState, 保证历史轮次召回的记忆文本不再对模型可见。
     * 重建结果由 {@link AgentHandle#isMemoryFreeState()} 缓存, 避免每轮重复重建。
     */
    private AgentHandle prepareHandle(Long userId, String sessionId, String memoryMode) {
        boolean requireClean = "NO_MEMORY".equalsIgnoreCase(memoryMode)
                || !memoryService.isEnabled(userId);
        AgentHandle handle = agentPool.getOrCreate(sessionId);
        if (requireClean && !handle.isMemoryFreeState()) {
            agentPool.resetState(userId, sessionId);
            handle = agentPool.getOrCreate(sessionId);
            handle.restoreHistory(userId, sessionId, chatMessageService.getHistory(sessionId));
            handle.markMemoryFreeState(true);
        }
        return handle;
    }

    private String normalizeMemoryMode(String value) {
        if (value == null || value.isBlank() || "NORMAL".equalsIgnoreCase(value)) {
            return "NORMAL";
        }
        if ("NO_MEMORY".equalsIgnoreCase(value)) {
            return "NO_MEMORY";
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的记忆模式");
    }

    private RetrievalContext performKnowledgeRetrieval(String message, String kbIds) {
        String augmented = message;
        List<RetrievalResultDto> results = List.of();

        // 只有用户主动勾选知识库（传入 kbIds）时才进行检索
        if (kbIds == null || kbIds.isBlank()) {
            log.trace("未指定知识库, 跳过检索");
            return new RetrievalContext(augmented, results);
        }

        try {
            boolean enabled = knowledgeRetrievalService.isEnabled(UserContext.getUserId());
            log.info("知识库检索: enabled={}, userId={}, kbIds={}", enabled, UserContext.getUserId(), kbIds);
            if (enabled) {
                boolean needsRetrieval = retrievalIntentService.needsRetrieval(message);
                if (needsRetrieval) {
                    List<Long> parsedKbIds = promptContextBuilder.parseKbIds(kbIds);
                    results = knowledgeRetrievalService.retrieve(UserContext.getUserId(), message, parsedKbIds);
                    log.info("知识库检索完成: 命中 {} 条", results.size());
                    if (!results.isEmpty()) {
                        augmented = knowledgeRetrievalService.buildAugmentedMessage(message, results);
                    }
                } else {
                    log.info("AI 判断无需检索知识库, 跳过检索");
                }
            }
        } catch (Exception e) {
            log.warn("知识检索异常, 降级为无检索模式: {}", e.getMessage(), e);
        }
        return new RetrievalContext(augmented, results);
    }
}
