package top.jionjion.agentdesk.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.session.Session;
import io.agentscope.core.state.SessionKey;
import io.agentscope.core.state.SimpleSessionKey;
import io.agentscope.core.state.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 基于 PostgreSQL 的 AgentScope Session 实现
 * <p>
 * agent_state 表中以 session_id + key 为唯一标识, state_data 存储 JSON 序列化的状态
 */
@Component
public class DatabaseSession implements Session {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSession.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbc;

    public DatabaseSession(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 保存单个状态到数据库, 存在则更新 */
    @Override
    public void save(SessionKey sessionKey, String key, State value) {
        String sessionId = extractSessionId(sessionKey);
        try {
            String json = MAPPER.writeValueAsString(value);
            jdbc.update("""
                    INSERT INTO agent_state (session_id, state_key, state_data, updated_at)
                    VALUES (?, ?, ?::jsonb, ?)
                    ON CONFLICT (session_id, state_key) DO UPDATE
                        SET state_data = EXCLUDED.state_data, updated_at = EXCLUDED.updated_at
                    """,
                    sessionId, key, json, System.currentTimeMillis());
        } catch (JsonProcessingException e) {
            log.error("序列化状态失败 session={}, key={}: {}", sessionId, key, e.getMessage());
        }
    }

    /** 保存状态列表到数据库, 存在则更新 */
    @Override
    public void save(SessionKey sessionKey, String key, List<? extends State> values) {
        String sessionId = extractSessionId(sessionKey);
        try {
            String json = MAPPER.writeValueAsString(values);
            jdbc.update("""
                    INSERT INTO agent_state (session_id, state_key, state_data, updated_at)
                    VALUES (?, ?, ?::jsonb, ?)
                    ON CONFLICT (session_id, state_key) DO UPDATE
                        SET state_data = EXCLUDED.state_data, updated_at = EXCLUDED.updated_at
                    """,
                    sessionId, key, json, System.currentTimeMillis());
        } catch (JsonProcessingException e) {
            log.error("序列化状态列表失败 session={}, key={}: {}", sessionId, key, e.getMessage());
        }
    }

    /** 获取单个状态, 反序列化为指定类型 */
    @Override
    public <T extends State> Optional<T> get(SessionKey sessionKey, String key, Class<T> type) {
        String sessionId = extractSessionId(sessionKey);
        List<String> results = jdbc.query(
                "SELECT state_data::text FROM agent_state WHERE session_id = ? AND state_key = ?",
                (rs, rowNum) -> rs.getString(1),
                sessionId, key);
        if (results.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(MAPPER.readValue(results.getFirst(), type));
        } catch (JsonProcessingException e) {
            log.warn("反序列化状态失败 session={}, key={}: {}", sessionId, key, e.getMessage());
            return Optional.empty();
        }
    }

    /** 获取状态列表, 反序列化为指定类型的集合 */
    @Override
    public <T extends State> List<T> getList(SessionKey sessionKey, String key, Class<T> itemType) {
        String sessionId = extractSessionId(sessionKey);
        List<String> results = jdbc.query(
                "SELECT state_data::text FROM agent_state WHERE session_id = ? AND state_key = ?",
                (rs, rowNum) -> rs.getString(1),
                sessionId, key);
        if (results.isEmpty()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(results.getFirst(),
                    MAPPER.getTypeFactory().constructCollectionType(List.class, itemType));
        } catch (JsonProcessingException e) {
            log.warn("反序列化状态列表失败 session={}, key={}: {}", sessionId, key, e.getMessage());
            return List.of();
        }
    }

    /** 判断会话是否存在状态记录 */
    @Override
    public boolean exists(SessionKey sessionKey) {
        String sessionId = extractSessionId(sessionKey);
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM agent_state WHERE session_id = ?",
                Integer.class, sessionId);
        return count != null && count > 0;
    }

    /** 删除会话的所有状态记录 */
    @Override
    public void delete(SessionKey sessionKey) {
        String sessionId = extractSessionId(sessionKey);
        jdbc.update("DELETE FROM agent_state WHERE session_id = ?", sessionId);
    }

    /** 列出所有存在状态记录的会话Key */
    @Override
    public Set<SessionKey> listSessionKeys() {
        List<String> ids = jdbc.query(
                "SELECT DISTINCT session_id FROM agent_state",
                (rs, rowNum) -> rs.getString(1));
        Set<SessionKey> keys = new HashSet<>();
        for (String id : ids) {
            keys.add(SimpleSessionKey.of(id));
        }
        return keys;
    }

    /** 关闭Session, JdbcTemplate由Spring管理无需手动关闭 */
    @Override
    public void close() {
        // JdbcTemplate 由 Spring 管理连接池, 无需手动关闭
    }

    /** 从SessionKey中提取会话ID字符串 */
    private String extractSessionId(SessionKey sessionKey) {
        if (sessionKey instanceof SimpleSessionKey simple) {
            return simple.sessionId();
        }
        return sessionKey.toString();
    }
}
