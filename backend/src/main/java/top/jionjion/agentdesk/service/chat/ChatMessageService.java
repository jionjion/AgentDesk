package top.jionjion.agentdesk.service.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import top.jionjion.agentdesk.entity.ChatMessage;
import top.jionjion.agentdesk.repository.ChatMessageRepository;
import top.jionjion.agentdesk.service.SessionService;
import top.jionjion.agentdesk.service.TitleGenerationService;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 对话消息持久化与会话收尾: 保存用户/助手消息、查找触发消息、首轮异步生成标题。
 *
 * @author Jion
 */
@Service
public class ChatMessageService {

    private static final Logger log = LoggerFactory.getLogger(ChatMessageService.class);
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    /**
     * 标题生成专用线程池, 避免阻塞 ForkJoinPool.commonPool 导致线程饥饿
     */
    private static final ExecutorService TITLE_EXECUTOR = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(128),
            r -> {
                Thread t = new Thread(r, "title-gen");
                t.setDaemon(true);
                return t;
            });

    private final ChatMessageRepository chatMessageRepository;
    private final SessionService sessionService;
    private final TitleGenerationService titleGenerationService;

    public ChatMessageService(ChatMessageRepository chatMessageRepository,
                              SessionService sessionService,
                              TitleGenerationService titleGenerationService) {
        this.chatMessageRepository = chatMessageRepository;
        this.sessionService = sessionService;
        this.titleGenerationService = titleGenerationService;
    }

    /**
     * 持久化用户消息
     */
    public void saveUserMessage(String sessionId, String message, List<Long> fileIds) {
        ChatMessage chatMsg = new ChatMessage(sessionId, ROLE_USER, message);
        if (fileIds != null && !fileIds.isEmpty()) {
            chatMsg.setFileIds(fileIds);
        }
        chatMessageRepository.save(chatMsg);
    }

    /**
     * 持久化助手回复, 返回数据库消息ID。回复为空时返回 null。
     */
    public Long saveAssistantReply(String sessionId, String reply) {
        if (reply == null || reply.isEmpty()) {
            return null;
        }
        ChatMessage saved = chatMessageRepository.save(new ChatMessage(sessionId, ROLE_ASSISTANT, reply));
        return saved.getId();
    }

    /**
     * 会话收尾: 更新会话最近使用时间, 首轮对话异步生成标题
     */
    public void finalizeSession(String sessionId, String triggeringMessage) {
        sessionService.touch(sessionId);
        if (sessionService.hasDefaultTitle(sessionId)) {
            TITLE_EXECUTOR.execute(() -> {
                try {
                    String generatedTitle = titleGenerationService.generateTitle(triggeringMessage);
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
     * 获取指定会话的全部消息 (按创建时间升序)
     */
    public List<ChatMessage> getHistory(String sessionId) {
        return chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    /**
     * 全文搜索当前用户所有会话中匹配关键词的消息
     */
    public List<ChatMessage> searchByContent(Long userId, String keyword) {
        return chatMessageRepository.searchByContent(userId, keyword);
    }

    /**
     * 删除指定消息
     */
    public void deleteById(Long messageId) {
        chatMessageRepository.deleteById(messageId);
    }

    /**
     * 校验目标消息为本会话的助手消息, 不满足抛出异常
     */
    public ChatMessage requireAssistantMessage(String sessionId, Long messageId) {
        ChatMessage targetMsg = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "消息不存在"));
        if (!targetMsg.getSessionId().equals(sessionId) || !ROLE_ASSISTANT.equals(targetMsg.getRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只能重新生成助手消息");
        }
        return targetMsg;
    }

    /**
     * 查找触发某条 assistant 消息的用户消息内容 (该消息之前最近的一条 user 消息)
     */
    @NonNull
    public String findTriggeringUserMessage(Long messageId, List<ChatMessage> history) {
        String userMessage = null;
        for (int i = 0; i < history.size(); i++) {
            if (history.get(i).getId().equals(messageId) && i > 0) {
                for (int j = i - 1; j >= 0; j--) {
                    if (ROLE_USER.equals(history.get(j).getRole())) {
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
        return userMessage;
    }
}
