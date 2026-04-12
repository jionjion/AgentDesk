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
 *
 * @author Jion
 */
public interface AgentStateRepository extends JpaRepository<AgentState, AgentStateId> {

    /**
     * 根据会话ID和状态键查询Agent状态
     *
     * @param sessionId 会话ID
     * @param stateKey  状态键
     * @return Agent状态
     */
    Optional<AgentState> findBySessionIdAndStateKey(String sessionId, String stateKey);

    /**
     * 判断指定会话是否存在Agent状态
     *
     * @param sessionId 会话ID
     * @return 是否存在
     */
    boolean existsBySessionId(String sessionId);

    /**
     * 删除指定会话的所有Agent状态
     *
     * @param sessionId 会话ID
     */
    @Transactional(rollbackFor = Exception.class)
    void deleteBySessionId(String sessionId);

    /**
     * 查询所有存在Agent状态的会话ID
     *
     * @return 会话ID列表
     */
    @Query("SELECT DISTINCT a.sessionId FROM AgentState a")
    List<String> findDistinctSessionIds();
}
