package top.jionjion.agentdesk.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import top.jionjion.agentdesk.session.AgentState;
import top.jionjion.agentdesk.session.AgentStateId;

import java.util.List;
import java.util.Optional;

/**
 * Agent 状态 — JPA 持久层
 */
public interface AgentStateRepository extends JpaRepository<AgentState, AgentStateId> {

    Optional<AgentState> findBySessionIdAndStateKey(String sessionId, String stateKey);

    boolean existsBySessionId(String sessionId);

    @Transactional
    void deleteBySessionId(String sessionId);

    @Query("SELECT DISTINCT a.sessionId FROM AgentState a")
    List<String> findDistinctSessionIds();
}
