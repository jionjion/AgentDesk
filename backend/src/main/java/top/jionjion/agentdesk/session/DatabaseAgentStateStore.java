package top.jionjion.agentdesk.session;

import com.fasterxml.jackson.core.type.TypeReference;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.State;
import io.agentscope.core.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import top.jionjion.agentdesk.entity.AgentState;
import top.jionjion.agentdesk.repository.AgentStateRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** PostgreSQL-backed AgentScope Java v2 state store. */
@Component
public class DatabaseAgentStateStore implements AgentStateStore {

    private static final Logger log = LoggerFactory.getLogger(DatabaseAgentStateStore.class);
    private static final String ANONYMOUS_USER = "__anonymous__";
    private static final TypeReference<List<Object>> OBJECT_LIST = new TypeReference<>() {};

    private final AgentStateRepository repository;
    public DatabaseAgentStateStore(AgentStateRepository repository) {
        this.repository = repository;
        log.info("AgentScope v2 state store: PostgreSQL");
    }

    @Override
    public void save(String userId, String sessionId, String key, State value) {
        write(userId, sessionId, key, value);
    }

    @Override
    public void save(String userId, String sessionId, String key, List<? extends State> values) {
        write(userId, sessionId, key, values);
    }

    @Override
    public <T extends State> Optional<T> get(
            String userId, String sessionId, String key, Class<T> type) {
        return find(userId, sessionId, key).flatMap(state -> {
            try {
                return Optional.of(JsonUtils.getJsonCodec().fromJson(state.getStateData(), type));
            } catch (RuntimeException ex) {
                log.warn("Unable to deserialize agent state: user={}, session={}, key={}",
                        userId, sessionId, key, ex);
                return Optional.empty();
            }
        });
    }

    @Override
    public <T extends State> List<T> getList(
            String userId, String sessionId, String key, Class<T> itemType) {
        return find(userId, sessionId, key).map(state -> {
            try {
                List<Object> values = JsonUtils.getJsonCodec()
                        .fromJson(state.getStateData(), OBJECT_LIST);
                return values.stream()
                        .map(value -> JsonUtils.getJsonCodec().convertValue(value, itemType))
                        .toList();
            } catch (RuntimeException ex) {
                log.warn("Unable to deserialize agent state list: user={}, session={}, key={}",
                        userId, sessionId, key, ex);
                return List.<T>of();
            }
        }).orElseGet(List::of);
    }

    @Override
    public boolean exists(String userId, String sessionId) {
        return repository.existsByUserIdAndSessionId(normalizeUserId(userId), sessionId);
    }

    @Override
    @Transactional
    public void delete(String userId, String sessionId) {
        repository.deleteByUserIdAndSessionId(normalizeUserId(userId), sessionId);
    }

    @Override
    @Transactional
    public void delete(String userId, String sessionId, String key) {
        repository.deleteByUserIdAndSessionIdAndStateKey(
                normalizeUserId(userId), sessionId, key);
    }

    @Override
    public Set<String> listSessionIds(String userId) {
        return new HashSet<>(repository.findDistinctSessionIdsByUserId(normalizeUserId(userId)));
    }

    private Optional<AgentState> find(String userId, String sessionId, String key) {
        return repository.findByUserIdAndSessionIdAndStateKey(
                normalizeUserId(userId), sessionId, key);
    }

    private void write(String userId, String sessionId, String key, Object value) {
        String normalizedUserId = normalizeUserId(userId);
        try {
            String json = JsonUtils.getJsonCodec().toJson(value);
            AgentState entity = repository
                    .findByUserIdAndSessionIdAndStateKey(normalizedUserId, sessionId, key)
                    .orElseGet(AgentState::new);
            entity.setUserId(normalizedUserId);
            entity.setSessionId(sessionId);
            entity.setStateKey(key);
            entity.setStateData(json);
            entity.setUpdatedAt(System.currentTimeMillis());
            repository.save(entity);
        } catch (RuntimeException ex) {
            throw new IllegalStateException(
                    "Unable to serialize agent state for session " + sessionId, ex);
        }
    }

    private String normalizeUserId(String userId) {
        return userId == null || userId.isBlank() ? ANONYMOUS_USER : userId;
    }
}
