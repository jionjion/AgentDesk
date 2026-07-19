package top.jionjion.agentdesk.entity;

import lombok.*;

import java.io.Serializable;

/**
 * agent_state_v2 表复合主键: user_id + session_id + state_key
 *
 * @author Jion
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class AgentStateId implements Serializable {

    private String userId;

    private String sessionId;

    private String stateKey;
}
