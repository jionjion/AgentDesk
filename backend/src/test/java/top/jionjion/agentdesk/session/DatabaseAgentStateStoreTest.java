package top.jionjion.agentdesk.session;

import io.agentscope.core.state.AgentState;
import org.junit.jupiter.api.Test;
import top.jionjion.agentdesk.repository.AgentStateRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseAgentStateStoreTest {

    @Test
    void roundTripsAgentScopeV2StateWithFrameworkCodec() {
        AgentStateRepository repository = mock(AgentStateRepository.class);
        Map<String, top.jionjion.agentdesk.entity.AgentState> rows = new HashMap<>();
        when(repository.findByUserIdAndSessionIdAndStateKey(any(), any(), any()))
                .thenAnswer(invocation -> Optional.ofNullable(rows.get(key(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(2)))));
        when(repository.save(any())).thenAnswer(invocation -> {
            top.jionjion.agentdesk.entity.AgentState row = invocation.getArgument(0);
            rows.put(key(row.getUserId(), row.getSessionId(), row.getStateKey()), row);
            return row;
        });

        DatabaseAgentStateStore store = new DatabaseAgentStateStore(repository);
        AgentState original = AgentState.builder()
                .userId("42")
                .sessionId("session-a")
                .summary("已完成迁移")
                .curIter(3)
                .build();

        store.save("42", "session-a", "agent_state", original);
        AgentState restored = store.get(
                "42", "session-a", "agent_state", AgentState.class).orElseThrow();

        assertEquals(original.getUserId(), restored.getUserId());
        assertEquals(original.getSessionId(), restored.getSessionId());
        assertEquals(original.getSummary(), restored.getSummary());
        assertEquals(original.getReplyId(), restored.getReplyId());
        assertEquals(original.getCurIter(), restored.getCurIter());
    }

    private static String key(String userId, String sessionId, String stateKey) {
        return userId + ":" + sessionId + ":" + stateKey;
    }
}
