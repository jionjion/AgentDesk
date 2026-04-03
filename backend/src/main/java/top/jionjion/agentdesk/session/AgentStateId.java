package top.jionjion.agentdesk.session;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * agent_state 表复合主键: session_id + state_key
 */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class AgentStateId implements Serializable {

    private String sessionId;

    private String stateKey;
}
