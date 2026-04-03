package top.jionjion.agentdesk.session;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Agent 序列化状态实体, 映射 agent_state 表
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "agent_state", schema = "agent_desk")
@IdClass(AgentStateId.class)
public class AgentState {

    /**
     * 会话ID
     */
    @Id
    @Column(name = "session_id", nullable = false, length = 32)
    private String sessionId;

    /**
     * 状态键名 (如 memory, toolkit 等)
     */
    @Id
    @Column(name = "state_key", nullable = false, length = 128)
    private String stateKey;

    /**
     * AgentScope 序列化的状态数据(JSON)
     */
    @Column(name = "state_data", columnDefinition = "jsonb", nullable = false)
    private String stateData;

    /**
     * 最后更新时间戳(毫秒)
     */
    @Column(name = "updated_at", nullable = false)
    private long updatedAt;

    public AgentState(String sessionId, String stateKey, String stateData) {
        this.sessionId = sessionId;
        this.stateKey = stateKey;
        this.stateData = stateData;
        this.updatedAt = System.currentTimeMillis();
    }
}
