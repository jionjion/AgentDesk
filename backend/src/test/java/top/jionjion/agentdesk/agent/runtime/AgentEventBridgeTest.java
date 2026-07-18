package top.jionjion.agentdesk.agent.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.ExceedMaxItersEvent;
import io.agentscope.core.event.ToolCallDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentEventBridgeTest {

    @Test
    void capturesFinalReplyFromTypedV2Event() {
        AgentEventBridge bridge = new AgentEventBridge(new ObjectMapper());
        Msg result = Msg.builder()
                .role(MsgRole.ASSISTANT)
                .textContent("完成")
                .build();

        bridge.accept(new AgentResultEvent(result));

        assertEquals("完成", bridge.getLastReply());
    }

    @Test
    void appendsMaxIterationHint() {
        AgentEventBridge bridge = new AgentEventBridge(new ObjectMapper());
        bridge.accept(new ExceedMaxItersEvent("reply-1", 30, 30));
        bridge.accept(new AgentResultEvent(Msg.builder()
                .role(MsgRole.ASSISTANT)
                .textContent("未完成")
                .build()));

        assertTrue(bridge.getLastReply().contains("已达到最大执行轮次限制"));
    }

    @Test
    void accumulatesAndParsesStreamedToolArguments() {
        AgentEventBridge bridge = new AgentEventBridge(new ObjectMapper());
        bridge.accept(new ToolCallStartEvent("reply-1", "tool-1", "web_search"));
        bridge.accept(new ToolCallDeltaEvent(
                "reply-1", "tool-1", "web_search", "{\"query\":\"AgentScope v2\","));
        bridge.accept(new ToolCallDeltaEvent(
                "reply-1", "tool-1", "web_search", "\"count\":5}"));

        assertEquals("AgentScope v2", bridge.parseArguments("tool-1").get("query"));
        assertEquals(5, bridge.parseArguments("tool-1").get("count"));
    }
}
