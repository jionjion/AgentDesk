package top.jionjion.agentdesk.agent.core;

import io.agentscope.core.state.AgentStateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import top.jionjion.agentdesk.repository.SessionRepository;
import top.jionjion.agentdesk.entity.SessionMetadata;
import top.jionjion.agentdesk.security.UserContext;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Agent 池: 管理 per-session 的 Agent 生命周期
 *
 * @author Jion
 */
@Service
public class AgentPool {

    private static final Logger log = LoggerFactory.getLogger(AgentPool.class);

    private final AgentFactory agentFactory;
    private final AgentStateStore stateStore;
    private final SessionRepository sessionRepository;

    private final ConcurrentHashMap<String, AgentHandle> agents = new ConcurrentHashMap<>();
    /**
     * 会话锁: 持有者 token。null 表示空闲, 非 null 表示被某次请求占用。
     * 释放时校验 token 一致, 避免延迟回调误放他人已重新获取的锁。
     */
    private final ConcurrentHashMap<String, AtomicReference<Object>> busyFlags = new ConcurrentHashMap<>();

    public AgentPool(AgentFactory agentFactory, AgentStateStore stateStore,
                     SessionRepository sessionRepository) {
        this.agentFactory = agentFactory;
        this.stateStore = stateStore;
        this.sessionRepository = sessionRepository;
        log.info("AgentScope v2 state store: {}", stateStore.getClass().getSimpleName());
    }

    /**
     * 获取或创建会话对应的 Agent
     */
    public AgentHandle getOrCreate(String sessionId) {
        return agents.computeIfAbsent(sessionId, id -> {
            log.info("为会话 {} 创建新的 Agent", id);
            return agentFactory.createAgent(id);
        });
    }

    /**
     * 删除会话及其 Agent
     */
    public void remove(String sessionId) {
        AgentHandle handle = agents.remove(sessionId);
        busyFlags.remove(sessionId);
        if (handle != null) {
            handle.close();
        }
        try {
            String userId = UserContext.isAuthenticated()
                    ? String.valueOf(UserContext.getUserId())
                    : null;
            stateStore.delete(userId, sessionId);
            log.info("已删除会话 {} 的 AgentScope v2 持久化状态", sessionId);
        } catch (Exception e) {
            log.warn("删除会话 {} 持久化状态失败: {}", sessionId, e.getMessage());
        }
    }

    /**
     * 使指定会话的 Agent 失效, 下次调用 getOrCreate 时会重建。
     * 仅移除内存中的引用, 不删除数据库中的对话状态。
     */
    public void invalidate(String sessionId) {
        AgentHandle handle = agents.remove(sessionId);
        if (handle != null) {
            handle.close();
            log.info("已使会话 {} 的 Agent 失效 (模型切换)", sessionId);
        }
    }

    /** Drops both the in-memory handle and the persisted v2 state for an exact regeneration. */
    public void resetState(Long userId, String sessionId) {
        invalidate(sessionId);
        stateStore.delete(userId == null ? null : String.valueOf(userId), sessionId);
        log.info("已重置会话 {} 的 AgentScope v2 状态", sessionId);
    }

    /** Interrupts only an existing in-flight session; never creates an agent as a side effect. */
    public boolean interrupt(Long userId, String sessionId) {
        AgentHandle handle = agents.get(sessionId);
        if (handle == null) {
            return false;
        }
        handle.interrupt(userId, sessionId);
        return true;
    }

    /**
     * 使指定用户所有会话的 Agent 失效。
     * 切换模型后调用, 确保后续对话使用新模型。
     */
    public void invalidateAll(Long userId) {
        List<SessionMetadata> sessions = sessionRepository.findByUserId(userId,
                Sort.by(Sort.Direction.DESC, "lastUsedAt"));
        int count = 0;
        for (SessionMetadata s : sessions) {
            AgentHandle handle = agents.remove(s.getId());
            if (handle != null) {
                handle.close();
                count++;
            }
        }
        if (count > 0) {
            log.info("已使用户 {} 的 {} 个 Agent 失效 (模型切换)", userId, count);
        }
    }

    /**
     * 尝试获取会话锁（防止并发调用同一 Agent）。
     * 获取成功返回持有者 token, 释放时需回传该 token; 获取失败返回 null。
     */
    public Object tryAcquire(String sessionId) {
        AtomicReference<Object> flag = busyFlags.computeIfAbsent(sessionId, key -> new AtomicReference<>());
        Object token = new Object();
        return flag.compareAndSet(null, token) ? token : null;
    }

    /**
     * 释放会话锁。仅当锁仍由 token 持有时才释放, 避免延迟回调误放他人已重新获取的锁。
     */
    public void release(String sessionId, Object token) {
        AtomicReference<Object> flag = busyFlags.get(sessionId);
        if (flag != null) {
            flag.compareAndSet(token, null);
        }
    }

    /**
     * 检查会话是否存在
     */
    public boolean exists(String sessionId) {
        return agents.containsKey(sessionId);
    }

    /**
     * 检查会话是否正在执行 (被某次请求占用)。
     * 用于会话切换项目前的守卫: 流式执行中的会话不允许切换项目。
     */
    public boolean isBusy(String sessionId) {
        AtomicReference<Object> flag = busyFlags.get(sessionId);
        return flag != null && flag.get() != null;
    }
}
