package top.jionjion.agentdesk.agent.core;

import io.agentscope.core.ReActAgent;
import top.jionjion.agentdesk.agent.hook.SseStreamingHook;
import top.jionjion.agentdesk.agent.tool.RemoteExecTool;

/**
 * Agent 及其关联 Hook 的句柄
 *
 * @author Jion
 */
public record AgentHandle(ReActAgent agent, SseStreamingHook hook, boolean longTermMemoryEnabled,
                           RemoteExecTool remoteExecTool) {
}
