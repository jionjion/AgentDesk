package top.jionjion.agentdesk.session;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.jionjion.agentdesk.agent.AgentPool;
import top.jionjion.agentdesk.dto.SessionResponse;
import top.jionjion.agentdesk.repository.ChatMessageRepository;
import top.jionjion.agentdesk.repository.SessionRepository;

import java.util.List;
import java.util.UUID;

/**
 * 会话管理服务
 */
@Service
public class SessionService {

    private final SessionRepository sessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final AgentPool agentPool;

    public SessionService(SessionRepository sessionRepository, ChatMessageRepository chatMessageRepository, AgentPool agentPool) {
        this.sessionRepository = sessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.agentPool = agentPool;
    }

    /**
     * 创建新会话
     */
    public SessionResponse create(String title, Long userId) {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        long now = System.currentTimeMillis();

        SessionMetadata metadata = new SessionMetadata();
        metadata.setId(id);
        metadata.setTitle(title != null ? title : "新对话");
        metadata.setCreatedAt(now);
        metadata.setLastUsedAt(now);
        metadata.setUserId(userId);
        sessionRepository.save(metadata);

        return toResponse(metadata);
    }

    /**
     * 列出当前用户的所有会话 (按最近使用排序)
     */
    public List<SessionResponse> listByUser(Long userId) {
        return sessionRepository.findByUserId(userId, Sort.by(Sort.Direction.DESC, "lastUsedAt")).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 获取当前用户的会话详情
     */
    public SessionResponse get(String id, Long userId) {
        return sessionRepository.findByIdAndUserId(id, userId).map(this::toResponse).orElse(null);
    }

    /**
     * 删除当前用户的会话
     */
    @Transactional
    public void delete(String id, Long userId) {
        sessionRepository.findByIdAndUserId(id, userId).ifPresent(m -> {
            chatMessageRepository.deleteBySessionId(id);
            sessionRepository.deleteById(id);
            agentPool.remove(id);
        });
    }

    /**
     * 更新当前用户的会话标题
     */
    public SessionResponse updateTitle(String id, String title, Long userId) {
        return sessionRepository.findByIdAndUserId(id, userId).map(m -> {
            m.setTitle(title);
            sessionRepository.save(m);
            return toResponse(m);
        }).orElse(null);
    }

    /**
     * 校验会话归属当前用户
     */
    public boolean belongsToUser(String sessionId, Long userId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId).isPresent();
    }

    /**
     * 更新最后使用时间
     */
    public void touch(String id) {
        sessionRepository.findById(id).ifPresent(m -> {
            m.setLastUsedAt(System.currentTimeMillis());
            sessionRepository.save(m);
        });
    }

    private SessionResponse toResponse(SessionMetadata metadata) {
        return new SessionResponse(
                metadata.getId(),
                metadata.getTitle(),
                metadata.getCreatedAt(),
                metadata.getLastUsedAt()
        );
    }
}
