package top.jionjion.agentdesk.service.chat;

import io.agentscope.core.message.Msg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import top.jionjion.agentdesk.agent.core.AgentHandle;
import top.jionjion.agentdesk.agent.core.AgentPool;
import top.jionjion.agentdesk.dto.chat.ChatRequest;
import top.jionjion.agentdesk.dto.file.FileResponse;
import top.jionjion.agentdesk.dto.knowledge.RetrievalResultDto;
import top.jionjion.agentdesk.security.UserContext;
import top.jionjion.agentdesk.service.FileService;
import top.jionjion.agentdesk.service.KnowledgeRetrievalService;
import top.jionjion.agentdesk.service.RetrievalIntentService;

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
    private final SseEmitterManager sseEmitterManager;
    private final KnowledgeRetrievalService knowledgeRetrievalService;
    private final RetrievalIntentService retrievalIntentService;

    public ChatStreamOrchestrator(AgentPool agentPool,
                                  FileService fileService,
                                  ChatMessageService chatMessageService,
                                  PromptContextBuilder promptContextBuilder,
                                  SseEmitterManager sseEmitterManager,
                                  KnowledgeRetrievalService knowledgeRetrievalService,
                                  RetrievalIntentService retrievalIntentService) {
        this.agentPool = agentPool;
        this.fileService = fileService;
        this.chatMessageService = chatMessageService;
        this.promptContextBuilder = promptContextBuilder;
        this.sseEmitterManager = sseEmitterManager;
        this.knowledgeRetrievalService = knowledgeRetrievalService;
        this.retrievalIntentService = retrievalIntentService;
    }

    /**
     * 启动一次新对话流。调用方需已通过鉴权且已 tryAcquire 成功获取会话锁, 并传入对应 token。
     */
    public SseEmitter startChat(ChatRequest chatRequest, Object lockToken) {
        String sessionId = chatRequest.sessionId();
        String message = chatRequest.message();
        try {
            AgentHandle handle = agentPool.getOrCreate(sessionId);
            SseEmitter emitter = sseEmitterManager.create();
            handle.hook().setEmitter(emitter);

            // 注入前端传入的工作目录到 RemoteExecTool
            if (handle.remoteExecTool() != null && chatRequest.workingDir() != null) {
                handle.remoteExecTool().setWorkingDir(chatRequest.workingDir());
            }

            // 解析文件并持久化用户消息
            List<Long> parsedFileIds = promptContextBuilder.parseFileIds(chatRequest.fileIds());
            List<FileResponse> files = parsedFileIds.isEmpty()
                    ? Collections.emptyList()
                    : fileService.getByIds(parsedFileIds);
            List<FileResponse> imageFiles = files.stream()
                    .filter(f -> promptContextBuilder.isImageFile(f.contentType())).toList();
            List<FileResponse> nonImageFiles = files.stream()
                    .filter(f -> !promptContextBuilder.isImageFile(f.contentType())).toList();
            chatMessageService.saveUserMessage(sessionId, message, parsedFileIds);

            // 知识库检索增强
            RetrievalContext retrieval = performKnowledgeRetrieval(message, chatRequest.kbIds());

            // 沙箱上下文增强
            String augmentedMessage = promptContextBuilder.buildSandboxAugmentedMessage(
                    retrieval.augmentedMessage(), chatRequest.sandboxContext());

            sseEmitterManager.configureCallbacks(emitter, sessionId, handle, () -> agentPool.release(sessionId, lockToken));
            sseEmitterManager.sendKnowledgeRetrieved(emitter, retrieval.results());
            if (handle.longTermMemoryEnabled()) {
                handle.hook().setLongTermMemoryEnabled(true);
            }

            // 构建用户消息并启动 Agent 流
            Msg userMsg = promptContextBuilder.buildUserMsg(augmentedMessage, imageFiles, nonImageFiles);
            subscribe(handle, userMsg, sessionId, message, emitter, lockToken);

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
    public SseEmitter regenerate(String sessionId, Long messageId, String userMessage, Object lockToken) {
        try {
            // 删除旧的 assistant 消息
            chatMessageService.deleteById(messageId);

            // 使 agent 失效并重建
            agentPool.invalidate(sessionId);
            AgentHandle handle = agentPool.getOrCreate(sessionId);
            SseEmitter emitter = sseEmitterManager.create();
            handle.hook().setEmitter(emitter);

            sseEmitterManager.configureCallbacks(emitter, sessionId, handle, () -> agentPool.release(sessionId, lockToken));
            if (handle.longTermMemoryEnabled()) {
                handle.hook().setLongTermMemoryEnabled(true);
            }

            Msg userMsg = Msg.builder().textContent(userMessage).build();
            subscribe(handle, userMsg, sessionId, userMessage, emitter, lockToken);

            return emitter;
        } catch (RuntimeException e) {
            agentPool.release(sessionId, lockToken);
            throw e;
        }
    }

    /**
     * 订阅 Agent 流, 处理完成、错误与释放
     */
    private void subscribe(AgentHandle handle, Msg userMsg,
                           String sessionId, String triggeringMessage, SseEmitter emitter, Object lockToken) {
        handle.agent().stream(userMsg)
                .doOnComplete(() -> onStreamComplete(sessionId, triggeringMessage, handle, emitter))
                .doOnError(e -> {
                    if (handle.hook().isClientDisconnected() || sseEmitterManager.isClientDisconnect(e)) {
                        log.debug("Session {} Agent流处理中客户端已断开", sessionId);
                    } else {
                        log.error("Agent error: {}", e.getMessage(), e);
                    }
                    try {
                        if (!handle.hook().isClientDisconnected()) {
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
                                  AgentHandle handle, SseEmitter emitter) {
        try {
            String reply = handle.hook().getLastReply();
            Long savedId = chatMessageService.saveAssistantReply(sessionId, reply);
            if (savedId != null && !handle.hook().isClientDisconnected()) {
                sseEmitterManager.sendMessageSaved(emitter, savedId);
            }
            agentPool.save(sessionId);
            chatMessageService.finalizeSession(sessionId, triggeringMessage);
            if (!handle.hook().isClientDisconnected()) {
                emitter.complete();
            }
        } catch (Exception e) {
            log.debug("Session {} SSE complete 时客户端已断开: {}", sessionId, e.getMessage());
        }
    }

    private record RetrievalContext(String augmentedMessage, List<RetrievalResultDto> results) {
    }

    private RetrievalContext performKnowledgeRetrieval(String message, String kbIds) {
        String augmented = message;
        List<RetrievalResultDto> results = List.of();

        // 只有用户主动勾选知识库（传入 kbIds）时才进行检索
        if (kbIds == null || kbIds.isBlank()) {
            log.info("未指定知识库, 跳过检索");
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
