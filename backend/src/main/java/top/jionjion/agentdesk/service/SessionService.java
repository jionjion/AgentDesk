package top.jionjion.agentdesk.service;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import top.jionjion.agentdesk.agent.core.AgentPool;
import top.jionjion.agentdesk.dto.session.SessionResponse;
import top.jionjion.agentdesk.entity.Project;
import top.jionjion.agentdesk.entity.SessionMetadata;
import top.jionjion.agentdesk.repository.ChatMessageRepository;
import top.jionjion.agentdesk.repository.ProjectRepository;
import top.jionjion.agentdesk.repository.SessionRepository;
import top.jionjion.agentdesk.security.UserContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 会话管理服务
 *
 * @author Jion
 */
@Service
public class SessionService {

    private final SessionRepository sessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ProjectRepository projectRepository;
    private final AgentPool agentPool;
    private final MemoryService memoryService;

    @Autowired
    public SessionService(SessionRepository sessionRepository, ChatMessageRepository chatMessageRepository,
                          ProjectRepository projectRepository, AgentPool agentPool,
                          MemoryService memoryService) {
        this.sessionRepository = sessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.projectRepository = projectRepository;
        this.agentPool = agentPool;
        this.memoryService = memoryService;
    }

    /** Test/backward-compatible constructor. */
    public SessionService(SessionRepository sessionRepository, ChatMessageRepository chatMessageRepository,
                          ProjectRepository projectRepository, AgentPool agentPool) {
        this(sessionRepository, chatMessageRepository, projectRepository, agentPool, null);
    }

    /**
     * 创建新会话, 可选绑定项目 (校验项目归属当前用户)
     */
    public SessionResponse create(String title, String projectId) {
        Long userId = UserContext.getUserId();
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        long now = System.currentTimeMillis();

        SessionMetadata metadata = new SessionMetadata();
        metadata.setId(id);
        metadata.setTitle(title != null ? title : "新对话");
        metadata.setCreatedAt(now);
        metadata.setLastUsedAt(now);
        metadata.setUserId(userId);
        metadata.setMemoryMode("NORMAL");
        if (projectId != null && !projectId.isBlank()) {
            Project project = projectRepository.findByIdAndUserId(projectId, userId)
                    .orElseThrow(() -> new IllegalArgumentException("项目不存在: " + projectId));
            metadata.setProjectId(project.getId());
        }
        sessionRepository.save(metadata);

        return toResponse(metadata);
    }

    /**
     * 列出当前用户的所有会话 (按最近使用排序)
     */
    public List<SessionResponse> listByUser() {
        Long userId = UserContext.getUserId();
        return sessionRepository.findByUserId(userId, Sort.by(Sort.Direction.DESC, "lastUsedAt")).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 获取当前用户的会话详情
     */
    public SessionResponse get(String id) {
        Long userId = UserContext.getUserId();
        return sessionRepository.findByIdAndUserId(id, userId).map(this::toResponse).orElse(null);
    }

    /**
     * 删除当前用户的会话
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        Long userId = UserContext.getUserId();
        sessionRepository.findByIdAndUserId(id, userId).ifPresent(m -> {
            if (memoryService != null) memoryService.invalidateSessionSources(userId, id);
            chatMessageRepository.deleteBySessionId(id);
            sessionRepository.deleteById(id);
            agentPool.remove(id);
        });
    }

    /**
     * 批量删除当前用户的会话
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteBatch(List<String> ids) {
        Long userId = UserContext.getUserId();
        for (String id : ids) {
            sessionRepository.findByIdAndUserId(id, userId).ifPresent(m -> {
                if (memoryService != null) memoryService.invalidateSessionSources(userId, id);
                chatMessageRepository.deleteBySessionId(id);
                sessionRepository.deleteById(id);
                agentPool.remove(id);
            });
        }
    }

    /**
     * 更新当前用户的会话标题
     */
    public SessionResponse updateTitle(String id, String title) {
        Long userId = UserContext.getUserId();
        return sessionRepository.findByIdAndUserId(id, userId).map(m -> {
            m.setTitle(title);
            sessionRepository.save(m);
            return toResponse(m);
        }).orElse(null);
    }

    /**
     * 校验会话归属当前用户
     */
    public boolean belongsToUser(String sessionId) {
        Long userId = UserContext.getUserId();
        return sessionRepository.findByIdAndUserId(sessionId, userId).isPresent();
    }

    /**
     * 内部更新标题 (不依赖 UserContext, 用于 Reactor 线程回调)
     */
    public void updateTitleInternal(String id, String title) {
        sessionRepository.findById(id).ifPresent(m -> {
            m.setTitle(title);
            sessionRepository.save(m);
        });
    }

    /**
     * 判断会话是否仍使用默认标题
     */
    public boolean hasDefaultTitle(String sessionId) {
        return sessionRepository.findById(sessionId)
                .map(m -> "新对话".equals(m.getTitle()))
                .orElse(false);
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

    /**
     * 获取当前用户的会话标题映射 (sessionId -> title)
     */
    public Map<String, String> getSessionTitleMap() {
        Long userId = UserContext.getUserId();
        return sessionRepository.findByUserId(userId, Sort.unsorted()).stream()
                .collect(Collectors.toMap(SessionMetadata::getId, SessionMetadata::getTitle));
    }

    /**
     * 绑定/解绑会话项目。projectId 为 null 时解绑。
     *
     * <p>规则 (见开发计划 6.2):
     * <ul>
     *   <li>会话正在流式执行时不允许切换项目</li>
     *   <li>切换成功后使内存 AgentHandle 失效, 下一轮重建并注入新项目上下文</li>
     *   <li>不删除会话历史</li>
     * </ul>
     *
     * @return 更新后的会话; 会话不存在返回 null
     * @throws IllegalStateException    会话正在执行
     * @throws IllegalArgumentException 项目不属于当前用户
     */
    public SessionResponse bindProject(String sessionId, String projectId) {
        Long userId = UserContext.getUserId();
        SessionMetadata metadata = sessionRepository.findByIdAndUserId(sessionId, userId).orElse(null);
        if (metadata == null) {
            return null;
        }
        if (agentPool.isBusy(sessionId)) {
            throw new IllegalStateException("会话正在执行中, 无法切换项目");
        }
        if (projectId == null || projectId.isBlank()) {
            metadata.setProjectId(null);
        } else {
            Project project = projectRepository.findByIdAndUserId(projectId, userId)
                    .orElseThrow(() -> new IllegalArgumentException("项目不存在: " + projectId));
            metadata.setProjectId(project.getId());
        }
        sessionRepository.save(metadata);
        // 使内存 Agent 失效, 下一轮重建时注入新的项目上下文; 不删除持久化对话状态
        agentPool.invalidate(sessionId);
        return toResponse(metadata);
    }

    public SessionResponse updateMemoryMode(String sessionId, String memoryMode) {
        Long userId = UserContext.getUserId();
        SessionMetadata metadata = sessionRepository.findByIdAndUserId(sessionId, userId).orElse(null);
        if (metadata == null) return null;
        if (agentPool.isBusy(sessionId)) {
            throw new IllegalStateException("会话正在执行中, 无法切换记忆模式");
        }
        String normalized = normalizeMemoryMode(memoryMode, false);
        boolean changed = !normalized.equals(metadata.getMemoryMode() == null ? "NORMAL" : metadata.getMemoryMode());
        metadata.setMemoryMode(normalized);
        sessionRepository.save(metadata);
        if (changed) {
            // 从原始消息重建 AgentState, 清除历史轮次注入的记忆/检索增强文本,
            // 避免切到 NO_MEMORY 后旧记忆仍留在模型上下文中 (与 regenerate 的重建方式一致)。
            agentPool.resetState(userId, sessionId);
            var handle = agentPool.getOrCreate(sessionId);
            handle.restoreHistory(userId, sessionId,
                    chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId));
            handle.markMemoryFreeState(true);
        }
        return toResponse(metadata);
    }

    /** Resolves INHERIT against the authenticated session's persisted default. */
    public String resolveMemoryMode(String sessionId, String requestedMode) {
        Long userId = UserContext.getUserId();
        SessionMetadata metadata = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        if (requestedMode == null || requestedMode.isBlank() || "INHERIT".equalsIgnoreCase(requestedMode)) {
            return normalizeMemoryMode(metadata.getMemoryMode(), false);
        }
        return normalizeMemoryMode(requestedMode, false);
    }

    private String normalizeMemoryMode(String value, boolean allowInherit) {
        if (value == null || value.isBlank() || "NORMAL".equalsIgnoreCase(value)) return "NORMAL";
        if ("NO_MEMORY".equalsIgnoreCase(value)) return "NO_MEMORY";
        if (allowInherit && "INHERIT".equalsIgnoreCase(value)) return "INHERIT";
        throw new IllegalArgumentException("memoryMode 只能是 NORMAL 或 NO_MEMORY");
    }

    private SessionResponse toResponse(SessionMetadata metadata) {
        String projectId = metadata.getProjectId();
        String projectName = null;
        if (projectId != null) {
            projectName = projectRepository.findById(projectId)
                    .map(Project::getName)
                    .orElse(null);
        }
        return new SessionResponse(
                metadata.getId(),
                metadata.getTitle(),
                metadata.getCreatedAt(),
                metadata.getLastUsedAt(),
                projectId,
                projectName,
                metadata.getMemoryMode() == null ? "NORMAL" : metadata.getMemoryMode()
        );
    }
}
