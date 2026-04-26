package top.jionjion.agentdesk.agent;

import io.agentscope.core.ReActAgent;

/**
 * Agent 及其关联 Hook 的句柄
 *
 * @author Jion
 */
public record AgentHandle(ReActAgent agent, SseStreamingHook hook, boolean longTermMemoryEnabled) {
}
