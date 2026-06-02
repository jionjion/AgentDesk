package top.jionjion.agentdesk.agent.core;

import io.agentscope.core.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import top.jionjion.agentdesk.repository.SessionRepository;
import top.jionjion.agentdesk.entity.SessionMetadata;

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
    private final Session session;
    private final SessionRepository sessionRepository;

    private final ConcurrentHashMap<String, AgentHandle> agents = new ConcurrentHashMap<>();
    /**
     * 会话锁: 持有者 token。null 表示空闲, 非 null 表示被某次请求占用。
     * 释放时校验 token 一致, 避免延迟回调误放他人已重新获取的锁。
     */
    private final ConcurrentHashMap<String, AtomicReference<Object>> busyFlags = new ConcurrentHashMap<>();

    public AgentPool(AgentFactory agentFactory, Session session, SessionRepository sessionRepository) {
        this.agentFactory = agentFactory;
        this.session = session;
        this.sessionRepository = sessionRepository;
        log.info("Agent 会话状态存储实现: {}", session.getClass().getSimpleName());
    }

    /**
     * 获取或创建会话对应的 Agent
     */
    public AgentHandle getOrCreate(String sessionId) {
        return agents.computeIfAbsent(sessionId, id -> {
            log.info("为会话 {} 创建新的 Agent", id);
            AgentHandle handle = agentFactory.createAgent(id);
            // 尝试从数据库恢复状态
            try {
                handle.agent().loadIfExists(session, id);
                log.info("已恢复会话 {} 的状态", id);
            } catch (Exception e) {
                log.warn("恢复会话 {} 状态失败: {}", id, e.getMessage());
            }
            return handle;
        });
    }

    /**
     * 保存会话状态
     */
    public void save(String sessionId) {
        AgentHandle handle = agents.get(sessionId);
        if (handle != null) {
            try {
                handle.agent().saveTo(session, sessionId);
                log.debug("已保存会话 {} 的状态", sessionId);
            } catch (Exception e) {
                log.warn("保存会话 {} 状态失败: {}", sessionId, e.getMessage());
            }
        }
    }

    /**
     * 删除会话及其 Agent
     */
    public void remove(String sessionId) {
        AgentHandle handle = agents.remove(sessionId);
        busyFlags.remove(sessionId);
        if (handle != null) {
            try {
                session.delete(io.agentscope.core.state.SimpleSessionKey.of(sessionId));
                log.info("已删除会话 {} 的 Agent 和持久化状态", sessionId);
            } catch (Exception e) {
                log.warn("删除会话 {} 持久化状态失败: {}", sessionId, e.getMessage());
            }
        }
    }

    /**
     * 使指定会话的 Agent 失效, 下次调用 getOrCreate 时会重建。
     * 仅移除内存中的引用, 不删除数据库中的对话状态。
     */
    public void invalidate(String sessionId) {
        AgentHandle handle = agents.remove(sessionId);
        if (handle != null) {
            log.info("已使会话 {} 的 Agent 失效 (模型切换)", sessionId);
        }
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
            if (agents.remove(s.getId()) != null) {
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
}
