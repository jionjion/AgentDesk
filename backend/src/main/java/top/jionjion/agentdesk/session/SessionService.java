package top.jionjion.agentdesk.session;

import org.springframework.stereotype.Service;
import top.jionjion.agentdesk.agent.AgentPool;
import top.jionjion.agentdesk.dto.SessionResponse;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 会话管理服务
 */
@Service
public class SessionService {

    private final SessionMetadataStore metadataStore;
    private final AgentPool agentPool;

    public SessionService(SessionMetadataStore metadataStore, AgentPool agentPool) {
        this.metadataStore = metadataStore;
        this.agentPool = agentPool;
    }

    /**
     * 创建新会话
     */
    public SessionResponse create(String title) {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        long now = System.currentTimeMillis();

        SessionMetadata metadata = new SessionMetadata(id, title != null ? title : "新对话", now, now);
        metadataStore.save(metadata);

        return toResponse(metadata);
    }

    /**
     * 列出所有会话 (按最近使用排序)
     */
    public List<SessionResponse> listAll() {
        return metadataStore.findAll().stream()
                .sorted(Comparator.comparingLong(SessionMetadata::getLastUsedAt).reversed())
                .map(this::toResponse)
                .toList();
    }

    /**
     * 获取会话详情
     */
    public SessionResponse get(String id) {
        return metadataStore.findById(id).map(this::toResponse).orElse(null);
    }

    /**
     * 删除会话
     */
    public void delete(String id) {
        metadataStore.deleteById(id);
        agentPool.remove(id);
    }

    /**
     * 更新会话标题
     */
    public SessionResponse updateTitle(String id, String title) {
        return metadataStore.findById(id).map(m -> {
            m.setTitle(title);
            metadataStore.save(m);
            return toResponse(m);
        }).orElse(null);
    }

    /**
     * 更新最后使用时间
     */
    public void touch(String id) {
        metadataStore.findById(id).ifPresent(m -> {
            m.setLastUsedAt(System.currentTimeMillis());
            metadataStore.save(m);
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
