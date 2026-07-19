package top.jionjion.agentdesk.dto.chat;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * SSE 子智能体事件载荷, 用于 subagent_event 事件。
 * <p>
 * AgentScope v2 会将子智能体(SubagentDeclaration)的内部事件以 source 打标后
 * 转发进主 Agent 事件流, 后端将其隔离为独立的 subagent_event, 由前端按 source
 * 分组渲染为可折叠面板, 避免混入主回复。
 *
 * @param source    来源路径, 如 "main/researcher"
 * @param agentId   子智能体名(source 末段), 如 "researcher", 前端分组键
 * @param eventType 事件子类型: start / text_chunk / thinking_chunk / tool_call_start / tool_call_end / complete
 * @param content   文本增量(text_chunk/thinking_chunk)、专家名(start)或最终结果(complete)
 * @param toolName  工具名称(tool_call_* 事件)
 * @param toolId    工具调用 ID(tool_call_* 事件)
 * @param arguments 工具调用参数(tool_call_start)
 * @param result    工具调用结果(tool_call_end)
 * @author Jion
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SubagentEventDto(
        String source,
        String agentId,
        String eventType,
        String content,
        String toolName,
        String toolId,
        Map<String, Object> arguments,
        String result
) {
}
