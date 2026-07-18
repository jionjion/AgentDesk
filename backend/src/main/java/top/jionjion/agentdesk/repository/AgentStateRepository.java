package top.jionjion.agentdesk.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.jionjion.agentdesk.entity.AgentState;
import top.jionjion.agentdesk.entity.AgentStateId;

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
    Optional<AgentState> findByUserIdAndSessionIdAndStateKey(
            String userId, String sessionId, String stateKey);

    /**
     * 判断指定会话是否存在Agent状态
     *
     * @param sessionId 会话ID
     * @return 是否存在
     */
    boolean existsByUserIdAndSessionId(String userId, String sessionId);

    /**
     * 删除指定会话的所有Agent状态 (批量DELETE, 不加载实体到持久化上下文)
     *
     * @param sessionId 会话ID
     */
    @Modifying
    @Query("DELETE FROM AgentState a WHERE a.userId = :userId AND a.sessionId = :sessionId")
    void deleteByUserIdAndSessionId(
            @Param("userId") String userId, @Param("sessionId") String sessionId);

    @Modifying
    @Query("DELETE FROM AgentState a WHERE a.userId = :userId AND a.sessionId = :sessionId AND a.stateKey = :stateKey")
    void deleteByUserIdAndSessionIdAndStateKey(
            @Param("userId") String userId,
            @Param("sessionId") String sessionId,
            @Param("stateKey") String stateKey);

    /**
     * 查询所有存在Agent状态的会话ID
     *
     * @return 会话ID列表
     */
    @Query("SELECT DISTINCT a.sessionId FROM AgentState a WHERE a.userId = :userId")
    List<String> findDistinctSessionIdsByUserId(@Param("userId") String userId);
}
