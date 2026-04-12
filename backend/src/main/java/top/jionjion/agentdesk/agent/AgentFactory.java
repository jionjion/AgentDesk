package top.jionjion.agentdesk.agent;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.tool.Toolkit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import top.jionjion.agentdesk.dto.ModelSettingsDto;
import top.jionjion.agentdesk.repository.FileRecordRepository;
import top.jionjion.agentdesk.security.UserContext;
import top.jionjion.agentdesk.service.OssService;
import top.jionjion.agentdesk.service.SettingsService;

/**
 * Agent 工厂: 为每个会话创建独立的 Agent 实例
 *
 * @author Jion
 */
@Component
public class AgentFactory {

    private static final String DEFAULT_SYS_PROMPT = """
            你是一个名为 Assistant 的智能助手协调者。你可以直接回答简单问题，也可以将复杂任务委派给专业的子助手。

            你有以下专家可以调用：
            - call_file_analyzer: 文件分析专家。当用户上传文件并需要分析（CSV结构、代码审查、配置校验、日志分析）时使用。
            - call_writer: 写作助手。当用户需要撰写邮件、润色文本、写周报、编写技术文档时使用。
            - call_coder: 编程助手。当用户需要代码生成、Bug分析、算法讲解、代码优化时使用。
            - call_data_analyst: 数据分析师。当用户上传数据并需要统计分析、趋势分析、异常检测时使用。

            当用户上传了文件时，消息中会包含文件的元信息 (文件名、大小、类型、fileId)。
            对于文件相关任务，请将 fileId 传递给相应的子助手。

            你也可以直接使用 get_current_time、calculate、read_file 等工具处理简单任务。
            不要猜测文件内容，请先调用 read_file 获取实际内容。

            请用中文回答。
            """;

    private final DashScopeChatModel defaultModel;
    private final FileRecordRepository fileRecordRepository;
    private final OssService ossService;
    private final SettingsService settingsService;

    @Value("${agentscope.dashscope.api-key}")
    private String apiKey;

    public AgentFactory(DashScopeChatModel defaultModel,
                        FileRecordRepository fileRecordRepository,
                        OssService ossService,
                        SettingsService settingsService) {
        this.defaultModel = defaultModel;
        this.fileRecordRepository = fileRecordRepository;
        this.ossService = ossService;
        this.settingsService = settingsService;
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

        // 读取用户模型配置
        DashScopeChatModel model = defaultModel;
        String sysPrompt = DEFAULT_SYS_PROMPT;
        if (UserContext.isAuthenticated()) {
            ModelSettingsDto ms = settingsService.getModelSettings(UserContext.getUserId());
            GenerateOptions options = GenerateOptions.builder()
                    .temperature(ms.temperature())
                    .topP(ms.topP())
                    .maxTokens(ms.maxTokens())
                    .build();
            model = DashScopeChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(ms.modelName())
                    .defaultOptions(options)
                    .build();
            if (ms.systemPrompt() != null && !ms.systemPrompt().isBlank()) {
                sysPrompt = ms.systemPrompt();
            }
        }

        // Lambda 捕获需要 effectively final
        final DashScopeChatModel finalModel = model;

        // 注册子代理: 文件分析专家
        toolkit.registration()
                .subAgent(() -> {
                    Toolkit subToolkit = new Toolkit();
                    subToolkit.registerTool(new FileTools(fileRecordRepository, ossService));
                    return ReActAgent.builder()
                            .name("FileAnalyzer")
                            .description("文件分析专家。擅长读取和分析上传的文件：CSV结构解析、代码审查、配置文件校验、日志分析。当用户上传文件并需要分析时调用。")
                            .sysPrompt("""
                                    你是文件分析专家。你的职责是读取用户上传的文件并进行深入分析。
                                    请先使用 read_file 工具获取文件内容，然后根据文件类型进行分析：
                                    - CSV文件：描述列结构、数据类型、行数、数据质量问题
                                    - 代码文件：进行代码审查，指出问题和改进建议
                                    - 配置文件：校验格式正确性，指出潜在问题
                                    - 日志文件：提取关键信息、错误模式、时间线
                                    请用中文回答，分析要具体、有条理。
                                    """)
                            .model(finalModel)
                            .toolkit(subToolkit)
                            .maxIters(5)
                            .build();
                })
                .apply();

        // 注册子代理: 写作助手
        toolkit.registration()
                .subAgent(() -> ReActAgent.builder()
                        .name("Writer")
                        .description("写作助手。擅长邮件撰写、文本润色、周报生成、技术文档编写。当用户需要写作相关帮助时调用。")
                        .sysPrompt("""
                                你是专业写作助手。你的职责是帮助用户完成各类写作任务：
                                - 邮件撰写：根据场景调整语气，正式/非正式皆可
                                - 文本润色：改善表达、修正语法、提升可读性
                                - 周报生成：结构化、突出重点成果和计划
                                - 技术文档：清晰准确，适当使用示例
                                请用中文回答，注重文字质量和结构。
                                """)
                        .model(finalModel)
                        .maxIters(3)
                        .build())
                .apply();

        // 注册子代理: 编程助手
        toolkit.registration()
                .subAgent(() -> {
                    Toolkit subToolkit = new Toolkit();
                    subToolkit.registerTool(new CalculateTools());
                    return ReActAgent.builder()
                            .name("Coder")
                            .description("编程助手。擅长代码生成、Bug分析、算法讲解、代码优化。当用户需要编程相关帮助时调用。")
                            .sysPrompt("""
                                    你是编程助手。你的职责是帮助用户解决编程问题：
                                    - 代码生成：根据需求生成高质量代码，包含注释
                                    - Bug分析：分析代码问题，给出修复方案
                                    - 算法讲解：用清晰的方式解释算法原理和复杂度
                                    - 代码优化：改善性能、可读性和最佳实践
                                    你可以使用 calculate 工具验证数学计算。
                                    请用中文回答，代码块使用合适的语言标记。
                                    """)
                            .model(finalModel)
                            .toolkit(subToolkit)
                            .maxIters(5)
                            .build();
                })
                .apply();

        // 注册子代理: 数据分析师
        toolkit.registration()
                .subAgent(() -> {
                    Toolkit subToolkit = new Toolkit();
                    subToolkit.registerTool(new FileTools(fileRecordRepository, ossService));
                    return ReActAgent.builder()
                            .name("DataAnalyst")
                            .description("数据分析师。擅长数据统计、趋势分析、异常检测、生成分析结论。当用户上传数据并需要分析洞察时调用。")
                            .sysPrompt("""
                                    你是数据分析师。你的职责是分析用户提供的数据并给出洞察：
                                    - 数据统计：计算均值、中位数、分布等基本统计量
                                    - 趋势分析：识别数据中的趋势和模式
                                    - 异常检测：发现异常值和数据质量问题
                                    - 分析结论：基于数据给出可执行的建议
                                    请先使用 read_file 工具获取数据内容，然后进行分析。
                                    请用中文回答，使用结构化的格式呈现分析结果。
                                    """)
                            .model(finalModel)
                            .toolkit(subToolkit)
                            .maxIters(5)
                            .build();
                })
                .apply();

        // 构建 ReActAgent
        ReActAgent agent = ReActAgent.builder()
                .name("assistant-" + sessionId)
                .sysPrompt(sysPrompt)
                .model(finalModel)
                .toolkit(toolkit)
                .memory(memory)
                .enablePlan()
                .hook(hook)
                .maxIters(10)
                .build();

        return new AgentHandle(agent, hook);
    }
}
