package top.jionjion.agentdesk.agent;

import io.agentscope.core.session.JsonSession;
import io.agentscope.core.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Agent 池: 管理 per-session 的 Agent 生命周期
 */
@Service
public class AgentPool {

    private static final Logger log = LoggerFactory.getLogger(AgentPool.class);

    private final AgentFactory agentFactory;
    private final Session session;

    private final ConcurrentHashMap<String, AgentHandle> agents = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicBoolean> busyFlags = new ConcurrentHashMap<>();

    public AgentPool(AgentFactory agentFactory,
                     @Value("${agentdesk.session.path:#{systemProperties['user.home'] + '/.agentdesk/sessions'}}") String sessionPath) {
        this.agentFactory = agentFactory;
        this.session = new JsonSession(Path.of(sessionPath));
        log.info("Agent 会话存储路径: {}", sessionPath);
    }

    /**
     * 获取或创建会话对应的 Agent
     */
    public AgentHandle getOrCreate(String sessionId) {
        return agents.computeIfAbsent(sessionId, id -> {
            log.info("为会话 {} 创建新的 Agent", id);
            AgentHandle handle = agentFactory.createAgent(id);
            // 尝试从持久化存储恢复状态
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
                log.info("已删除会话 {} 的 Agent 和持久化数据", sessionId);
            } catch (Exception e) {
                log.warn("删除会话 {} 持久化数据失败: {}", sessionId, e.getMessage());
            }
        }
    }

    /**
     * 尝试获取会话锁（防止并发调用同一 Agent）
     * @return true 如果成功获取锁
     */
    public boolean tryAcquire(String sessionId) {
        AtomicBoolean flag = busyFlags.computeIfAbsent(sessionId, id -> new AtomicBoolean(false));
        return flag.compareAndSet(false, true);
    }

    /**
     * 释放会话锁
     */
    public void release(String sessionId) {
        AtomicBoolean flag = busyFlags.get(sessionId);
        if (flag != null) {
            flag.set(false);
        }
    }

    /**
     * 检查会话是否存在
     */
    public boolean exists(String sessionId) {
        return agents.containsKey(sessionId);
    }
}
