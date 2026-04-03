package top.jionjion.agentdesk.agent;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.tool.Toolkit;
import org.springframework.stereotype.Component;

/**
 * Agent 工厂: 为每个会话创建独立的 Agent 实例
 */
@Component
public class AgentFactory {

    private static final String SYS_PROMPT = """
            你是一个名为 Assistant 的智能助手。你可以帮助用户回答问题、获取当前时间、进行数学计算等。
            当遇到复杂任务时，你可以制定计划并逐步执行。
            请用中文回答。
            """;

    private final DashScopeChatModel model;

    public AgentFactory(DashScopeChatModel model) {
        this.model = model;
    }

    /**
     * 创建一个新的 Agent 实例（含 Toolkit 和 Hook）
     */
    public AgentHandle createAgent(String sessionId) {
        // 每个 Agent 独立的 Toolkit（Toolkit 有状态, 不可共享）
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new SimpleTools());

        // 每个 Agent 独立的 Hook
        SseStreamingHook hook = new SseStreamingHook();

        // 每个 Agent 独立的 Memory
        InMemoryMemory memory = new InMemoryMemory();

        // 构建 ReActAgent
        ReActAgent agent = ReActAgent.builder()
                .name("assistant-" + sessionId)
                .sysPrompt(SYS_PROMPT)
                .model(model)
                .toolkit(toolkit)
                .memory(memory)
                .enablePlan()
                .hook(hook)
                .maxIters(10)
                .build();

        return new AgentHandle(agent, hook);
    }
}
