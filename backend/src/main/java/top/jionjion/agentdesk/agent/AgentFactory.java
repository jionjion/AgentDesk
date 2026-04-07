package top.jionjion.agentdesk.agent;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.tool.Toolkit;
import org.springframework.stereotype.Component;
import top.jionjion.agentdesk.repository.FileRecordRepository;
import top.jionjion.agentdesk.service.OssService;

/**
 * Agent 工厂: 为每个会话创建独立的 Agent 实例
 */
@Component
public class AgentFactory {

    private static final String SYS_PROMPT = """
            你是一个名为 Assistant 的智能助手。你可以帮助用户回答问题、获取当前时间、进行数学计算等。
            当遇到复杂任务时，你可以制定计划并逐步执行。

            当用户上传了文件时，消息中会包含文件的元信息 (文件名、大小、类型、fileId)。
            你可以使用 read_file 工具读取文件内容，然后基于内容回答用户的问题。
            不要猜测文件内容，请先调用 read_file 获取实际内容。

            请用中文回答。
            """;

    private final DashScopeChatModel model;
    private final FileRecordRepository fileRecordRepository;
    private final OssService ossService;

    public AgentFactory(DashScopeChatModel model, FileRecordRepository fileRecordRepository, OssService ossService) {
        this.model = model;
        this.fileRecordRepository = fileRecordRepository;
        this.ossService = ossService;
    }

    /**
     * 创建一个新的 Agent 实例（含 Toolkit 和 Hook）
     */
    public AgentHandle createAgent(String sessionId) {
        // 每个 Agent 独立的 Toolkit（Toolkit 有状态, 不可共享）
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new SimpleTools(fileRecordRepository, ossService));

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
