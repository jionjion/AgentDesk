package top.jionjion.agentdesk.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.session.Session;
import io.agentscope.core.state.SessionKey;
import io.agentscope.core.state.SimpleSessionKey;
import io.agentscope.core.state.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import top.jionjion.agentdesk.repository.AgentStateRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 基于 PostgreSQL + JPA 的 AgentScope Session 实现
 * <p>
 * agent_state 表中以 session_id + state_key 为唯一标识, state_data 存储 JSON 序列化的状态
 *
 * @author Jion
 */
@Component
public class DatabaseSession implements Session {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSession.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AgentStateRepository repository;

    public DatabaseSession(AgentStateRepository repository) {
        this.repository = repository;
    }

    /**
     * 保存单个状态到数据库, 存在则更新
     */
    @Override
    public void save(SessionKey sessionKey, String key, State value) {
        String sessionId = extractSessionId(sessionKey);
        try {
            String json = MAPPER.writeValueAsString(value);
            saveState(sessionId, key, json);
        } catch (JsonProcessingException e) {
            log.error("序列化状态失败 session={}, key={}: {}", sessionId, key, e.getMessage());
        }
    }

    /**
     * 保存状态列表到数据库, 存在则更新
     */
    @Override
    public void save(SessionKey sessionKey, String key, List<? extends State> values) {
        String sessionId = extractSessionId(sessionKey);
        try {
            String json = MAPPER.writeValueAsString(values);
            saveState(sessionId, key, json);
        } catch (JsonProcessingException e) {
            log.error("序列化状态列表失败 session={}, key={}: {}", sessionId, key, e.getMessage());
        }
    }

    /**
     * 获取单个状态, 反序列化为指定类型
     */
    @Override
    public <T extends State> Optional<T> get(SessionKey sessionKey, String key, Class<T> type) {
        String sessionId = extractSessionId(sessionKey);
        Optional<AgentState> state = repository.findBySessionIdAndStateKey(sessionId, key);
        if (state.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(MAPPER.readValue(state.get().getStateData(), type));
        } catch (JsonProcessingException e) {
            log.warn("反序列化状态失败 session={}, key={}: {}", sessionId, key, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 获取状态列表, 反序列化为指定类型的集合
     */
    @Override
    public <T extends State> List<T> getList(SessionKey sessionKey, String key, Class<T> itemType) {
        String sessionId = extractSessionId(sessionKey);
        Optional<AgentState> state = repository.findBySessionIdAndStateKey(sessionId, key);
        if (state.isEmpty()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(state.get().getStateData(),
                    MAPPER.getTypeFactory().constructCollectionType(List.class, itemType));
        } catch (JsonProcessingException e) {
            log.warn("反序列化状态列表失败 session={}, key={}: {}", sessionId, key, e.getMessage());
            return List.of();
        }
    }

    /**
     * 判断会话是否存在状态记录
     */
    @Override
    public boolean exists(SessionKey sessionKey) {
        String sessionId = extractSessionId(sessionKey);
        return repository.existsBySessionId(sessionId);
    }

    /**
     * 删除会话的所有状态记录
     */
    @Override
    @Transactional
    public void delete(SessionKey sessionKey) {
        String sessionId = extractSessionId(sessionKey);
        repository.deleteBySessionId(sessionId);
    }

    /**
     * 列出所有存在状态记录的会话Key
     */
    @Override
    public Set<SessionKey> listSessionKeys() {
        List<String> ids = repository.findDistinctSessionIds();
        Set<SessionKey> keys = new HashSet<>();
        for (String id : ids) {
            keys.add(SimpleSessionKey.of(id));
        }
        return keys;
    }

    /**
     * 关闭Session, JPA由Spring管理无需手动关闭
     */
    @Override
    public void close() {
        // JPA 由 Spring 管理, 无需手动关闭
    }

    /**
     * 保存或更新状态
     */
    private void saveState(String sessionId, String key, String json) {
        AgentState entity = repository.findBySessionIdAndStateKey(sessionId, key)
                .orElseGet(() -> {
                    AgentState s = new AgentState();
                    s.setSessionId(sessionId);
                    s.setStateKey(key);
                    return s;
                });
        entity.setStateData(json);
        entity.setUpdatedAt(System.currentTimeMillis());
        repository.save(entity);
    }

    /**
     * 从SessionKey中提取会话ID字符串
     */
    private String extractSessionId(SessionKey sessionKey) {
        if (sessionKey instanceof SimpleSessionKey(String sessionId)) {
            return sessionId;
        }
        return sessionKey.toString();
    }
}
