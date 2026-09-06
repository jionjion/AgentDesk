package top.jionjion.agentdesk.agent.runtime;

import java.util.Map;

/**
 * 一次 Agent 调用的租户与会话上下文。
 *
 * <p>字段与 AgentScope v2 {@code RuntimeContext} 对齐，并在每次调用时原样映射。
 * {@code projectContext} 作为类型化属性放入 RuntimeContext, 由工具方法按类型注入。
 */
public record AgentRunContext(String userId, String sessionId, Map<String, Object> attributes,
                              ProjectRuntimeContext projectContext, MemoryRuntimeContext memoryContext) {

    public AgentRunContext {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static AgentRunContext of(Long userId, String sessionId) {
        return new AgentRunContext(userId == null ? null : String.valueOf(userId), sessionId,
                Map.of(), null, new MemoryRuntimeContext(true));
    }

    public static AgentRunContext of(Long userId, String sessionId, ProjectRuntimeContext projectContext) {
        return new AgentRunContext(userId == null ? null : String.valueOf(userId), sessionId,
                Map.of(), projectContext, new MemoryRuntimeContext(true));
    }

    public static AgentRunContext of(Long userId, String sessionId, ProjectRuntimeContext projectContext,
                                     String memoryMode) {
        return of(userId, sessionId, projectContext, memoryMode, null);
    }

    public static AgentRunContext of(Long userId, String sessionId, ProjectRuntimeContext projectContext,
                                     String memoryMode, Long userMessageId) {
        return new AgentRunContext(userId == null ? null : String.valueOf(userId), sessionId,
                Map.of(), projectContext,
                new MemoryRuntimeContext(!"NO_MEMORY".equalsIgnoreCase(memoryMode), sessionId, userMessageId));
    }
}
