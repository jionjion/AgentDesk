package top.jionjion.agentdesk.agent;

import io.agentscope.core.ReActAgent;

/**
 * Agent 及其关联 Hook 的句柄
 */
public record AgentHandle(ReActAgent agent, SseStreamingHook hook) {
}
