package top.jionjion.agentdesk.agent.runtime;

import java.util.Map;

/**
 * 一次 Agent 调用的租户与会话上下文。
 *
 * <p>字段与 AgentScope v2 {@code RuntimeContext} 对齐，并在每次调用时原样映射。
 */
public record AgentRunContext(String userId, String sessionId, Map<String, Object> attributes) {

    public AgentRunContext {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static AgentRunContext of(Long userId, String sessionId) {
        return new AgentRunContext(userId == null ? null : String.valueOf(userId), sessionId, Map.of());
    }
}
