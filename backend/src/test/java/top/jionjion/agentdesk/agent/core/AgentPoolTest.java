package top.jionjion.agentdesk.agent.core;

import io.agentscope.core.state.AgentStateStore;
import org.junit.jupiter.api.Test;
import top.jionjion.agentdesk.repository.SessionRepository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentPoolTest {

    @Test
    void interruptTargetsExistingSessionWithExplicitUserAndDoesNotCreateMissingAgent() {
        AgentFactory factory = mock(AgentFactory.class);
        AgentStateStore stateStore = mock(AgentStateStore.class);
        AgentPool pool = new AgentPool(factory, stateStore, mock(SessionRepository.class));

        assertFalse(pool.interrupt(7L, "missing"));
        verifyNoInteractions(factory);

        AgentHandle handle = mock(AgentHandle.class);
        when(factory.createAgent("session-1")).thenReturn(handle);
        pool.getOrCreate("session-1");

        assertTrue(pool.interrupt(7L, "session-1"));
        verify(handle).interrupt(7L, "session-1");
    }
}
