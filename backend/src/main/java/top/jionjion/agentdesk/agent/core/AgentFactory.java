package top.jionjion.agentdesk.agent.core;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.memory.LongTermMemoryMode;
import io.agentscope.core.memory.mem0.Mem0ApiType;
import io.agentscope.core.memory.mem0.Mem0LongTermMemory;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.skill.repository.ClasspathSkillRepository;
import io.agentscope.core.skill.repository.FileSystemSkillRepository;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.subagent.SubAgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import top.jionjion.agentdesk.agent.hook.SseStreamingHook;
import top.jionjion.agentdesk.agent.tool.ApiCallTool;
import top.jionjion.agentdesk.agent.tool.BatchWebResearchTool;
import top.jionjion.agentdesk.agent.tool.CommandRiskClassifier;
import top.jionjion.agentdesk.agent.tool.DynamicAgentTool;
import top.jionjion.agentdesk.agent.tool.IpLocationTool;
import top.jionjion.agentdesk.agent.tool.RemoteExecTool;
import top.jionjion.agentdesk.agent.tool.SandboxExecTool;
import top.jionjion.agentdesk.agent.tool.SimpleTools;
import top.jionjion.agentdesk.agent.tool.ToolDefinitions;
import top.jionjion.agentdesk.agent.tool.WebTools;
import top.jionjion.agentdesk.dto.settings.MemorySettingsDto;
import top.jionjion.agentdesk.dto.settings.ModelSettingsDto;
import top.jionjion.agentdesk.entity.McpServer;
import top.jionjion.agentdesk.entity.Skill;
import top.jionjion.agentdesk.repository.FileRecordRepository;
import top.jionjion.agentdesk.security.UserContext;
import top.jionjion.agentdesk.service.McpServerService;
import top.jionjion.agentdesk.service.OssService;
import top.jionjion.agentdesk.service.SettingsService;
import top.jionjion.agentdesk.service.SkillService;
import top.jionjion.agentdesk.websocket.RemoteExecBridge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Agent 工厂: 为每个会话创建独立的 Agent 实例
 * <p>
 * 使用 AgentScope SkillBox 系统进行技能管理:
 * - 内置技能通过 ClasspathSkillRepository 加载
 * - 用户安装的技能通过 FileSystemSkillRepository 加载
 * - 远程命令执行通过 RemoteExecTool (WebSocket) 实现
 *
 * @author Jion
 */
@Component
public class AgentFactory {

    private static final Logger log = LoggerFactory.getLogger(AgentFactory.class);

    private static final String SYS_PROMPT = """
            你是一个名为 Assistant 的智能助手。你可以直接回答简单问题，也可以使用已加载的技能完成复杂任务。

            当用户上传了文件时，消息中会包含文件的元信息 (文件名、大小、类型、fileId)。
            对于文件相关任务，请将 fileId 传递给相应的工具。

            你也可以直接使用 get_current_time、calculate、read_file、api_call 等工具处理简单任务。
            不要猜测文件内容，请先调用 read_file 获取实际内容。
            当用户需要调用第三方 API 或测试接口时，使用 api_call 工具发送 HTTP 请求。

            当用户需要搜索互联网、查询网页内容或获取最新资讯时，使用 web_researcher 子代理。
            当用户提出复杂问题需要多角度调研、交叉验证时，使用 deep_researcher 子代理。
            当用户需要对某个主题进行广泛调研（如了解现状、对比方案、趋势分析）时，使用 batch_web_researcher 工具并行搜索多个角度。
            当用户需要翻译文本或文档时，使用 translator 子代理。
            当用户提交代码要求审查、分析、优化、找bug或看看有没有问题时，必须使用 code_reviewer 子代理。
            当用户需要对长文本、文档、日志、对话进行总结、提炼要点时，使用 summarizer 子代理。
            当用户提出复杂目标需要拆解步骤、制定计划、规划路线时，使用 planner 子代理。
            当用户的请求涉及多个步骤时（如深度调研、批量处理、多阶段分析），请先使用 create_plan 创建执行计划，然后按步骤执行，每完成一个子任务使用 finish_subtask 标记完成。这样用户可以在界面上看到实时进度。
            当现有子代理都不适合当前任务时，可以使用 create_agent 工具动态创建一个临时子代理，自定义其角色、目标和工具组合来完成特定任务。
            传入清晰的任务描述即可，子代理会返回精简的结果。

            重要: 在调用任何工具或子代理之前，先用一句话告知用户你接下来要做什么。例如:
            - "我来帮你查一下当前时间。"
            - "我来读取这个文件的内容。"
            - "我来搜索一下相关信息。"
            - "我来帮你调用这个接口。"
            - "我来分析一下这段代码。"
            这样用户可以了解你的操作意图。

            工具调用规则: 如果同一个工具连续调用失败（返回 Error），最多重试 2 次。
            超过 2 次后不要再重试，直接告知用户该工具暂时不可用，并尝试用其他方式回答。

            请用中文回答。
            """;

    private final ChatModelFactory chatModelFactory;
    private final FileRecordRepository fileRecordRepository;
    private final OssService ossService;
    private final SettingsService settingsService;
    private final SkillService skillService;
    private final McpServerService mcpServerService;
    private final McpConnectionManager mcpConnectionManager;
    private final RemoteExecBridge remoteExecBridge;
    private final CommandRiskClassifier commandRiskClassifier;
    private final String mem0BaseUrl;
    private final String mem0ApiKey;
    private final String skillsBaseDir;
    private final String tavilyApiKey;
    private final boolean remoteExecEnabled;

    public AgentFactory(ChatModelFactory chatModelFactory,
                        FileRecordRepository fileRecordRepository,
                        OssService ossService,
                        SettingsService settingsService,
                        SkillService skillService,
                        McpServerService mcpServerService,
                        McpConnectionManager mcpConnectionManager,
                        RemoteExecBridge remoteExecBridge,
                        CommandRiskClassifier commandRiskClassifier,
                        @Value("${agentdesk.mem0.base-url}") String mem0BaseUrl,
                        @Value("${agentdesk.mem0.api-key:}") String mem0ApiKey,
                        @Value("${agentdesk.skills.base-dir}") String skillsBaseDir,
                        @Value("${agentdesk.tools.web-search.api-key:}") String tavilyApiKey,
                        @Value("${agentdesk.remote-exec.enabled:false}") boolean remoteExecEnabled) {
        this.chatModelFactory = chatModelFactory;
        this.fileRecordRepository = fileRecordRepository;
        this.ossService = ossService;
        this.settingsService = settingsService;
        this.skillService = skillService;
        this.mcpServerService = mcpServerService;
        this.mcpConnectionManager = mcpConnectionManager;
        this.remoteExecBridge = remoteExecBridge;
        this.commandRiskClassifier = commandRiskClassifier;
        this.mem0BaseUrl = mem0BaseUrl.endsWith("/") ? mem0BaseUrl.substring(0, mem0BaseUrl.length() - 1) : mem0BaseUrl;
        this.mem0ApiKey = (mem0ApiKey == null || mem0ApiKey.isBlank()) ? null : mem0ApiKey;
        this.skillsBaseDir = skillsBaseDir;
        this.tavilyApiKey = (tavilyApiKey == null || tavilyApiKey.isBlank()) ? null : tavilyApiKey;
        this.remoteExecEnabled = remoteExecEnabled;
    }

    /**
     * 创建一个新的 Agent 实例（含 SkillBox、Toolkit 和 Hook）
     */
    public AgentHandle createAgent(String sessionId) {
        // 每个 Agent 独立的 Toolkit（Toolkit 有状态, 不可共享）
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new ApiCallTool());
        toolkit.registerTool(new IpLocationTool());

        // 每个 Agent 独立的 Hook
        SseStreamingHook hook = new SseStreamingHook();

        // 每个 Agent 独立的 Memory
        InMemoryMemory memory = new InMemoryMemory();

        // 读取用户模型配置 + API Key
        ModelSettingsDto ms = ModelSettingsDto.defaults();
        MemorySettingsDto memSettings = MemorySettingsDto.defaults();
        String userApiKey = null;
        String sysPrompt = SYS_PROMPT;
        Long userId = null;

        if (UserContext.isAuthenticated()) {
            userId = UserContext.getUserId();
            ms = settingsService.getModelSettings(userId);
            userApiKey = settingsService.getDashScopeApiKey(userId);
            memSettings = settingsService.getMemorySettings(userId);
        }

        toolkit.registerTool(new SimpleTools(fileRecordRepository, ossService, userId));

        // 通过工厂创建模型（自动 fallback 到系统默认 Key）
        DashScopeChatModel model = chatModelFactory.create(ms, userApiKey);

        // 构建 SkillBox（核心变更: 从 sub-agent 模式迁移到 SkillBox 模式）
        SkillBox skillBox = buildSkillBox(toolkit, userId);

        // MCP 服务器集成: 连接已启用的 MCP 服务器并注册工具
        if (userId != null) {
            List<McpServer> mcpServers = mcpServerService.getEnabledServers(userId);
            if (!mcpServers.isEmpty()) {
                Map<Long, Boolean> results = mcpConnectionManager.connectAndRegister(toolkit, mcpServers);
                // 回写连接结果: 成功清零失败计数, 连续失败超阈值自动熔断禁用
                mcpServerService.applyConnectionResults(results);
                log.info("已为会话 {} 注册 {} 个 MCP 服务器", sessionId, mcpServers.size());
            }
        }

        // 远程执行工具: 始终注册（工具内部会检查客户端连接状态）
        RemoteExecTool remoteExecTool = null;
        if (remoteExecEnabled && userId != null) {
            remoteExecTool = new RemoteExecTool(remoteExecBridge, commandRiskClassifier, userId, sessionId);
            toolkit.registerTool(remoteExecTool);
            // 将客户端 OS 信息注入系统提示词, 让 Agent 知道目标平台
            String clientPlatform = remoteExecBridge.getClientPlatform(userId);
            if (clientPlatform != null) {
                sysPrompt += "\n\n[远程执行环境] 用户客户端操作系统: " + remoteExecTool.getOsDescription()
                        + "。请确保 remote_exec 工具中使用的命令与该操作系统兼容。";
            }
            log.info("已为会话 {} 注册远程执行工具 (userId={}, platform={})", sessionId, userId, clientPlatform);

            // 沙箱执行工具: 当客户端连接时始终注册
            SandboxExecTool sandboxExecTool = new SandboxExecTool(remoteExecBridge, userId, sessionId);
            toolkit.registerTool(sandboxExecTool);
            log.info("已为会话 {} 注册沙箱执行工具", sessionId);
        }

        // 子代理集成: 注册 web-researcher 子代理（通过 Agent as Tool 模式）
        registerSubAgents(toolkit, model, userId);

        log.info("会话 {} 工具注册完成, 已注册工具: {}", sessionId, toolkit.getToolNames());

        // 用户自定义系统提示词优先
        if (ms.systemPrompt() != null && !ms.systemPrompt().isBlank()) {
            sysPrompt = ms.systemPrompt();
        }

        // 构建 ReActAgent（集成 SkillBox）
        ReActAgent.Builder builder = ReActAgent.builder()
                .name("assistant-" + sessionId)
                .sysPrompt(sysPrompt)
                .model(model)
                .toolkit(toolkit)
                .skillBox(skillBox)
                .memory(memory)
                .enablePlan()
                .hook(hook)
                .maxIters(30);

        // 如果启用了长期记忆, 注入 Mem0LongTermMemory
        boolean ltmEnabled = false;
        if (memSettings.enabled() != null && memSettings.enabled() && userId != null) {
            try {
                Mem0LongTermMemory.Builder ltmBuilder = Mem0LongTermMemory.builder()
                        .agentName("assistant")
                        .userId(String.valueOf(userId))
                        .apiBaseUrl(mem0BaseUrl)
                        .apiType(Mem0ApiType.SELF_HOSTED);
                if (mem0ApiKey != null) {
                    ltmBuilder.apiKey(mem0ApiKey);
                }
                builder.longTermMemory(ltmBuilder.build())
                        .longTermMemoryMode(LongTermMemoryMode.AGENT_CONTROL);
                ltmEnabled = true;
                log.info("已为会话 {} 启用长期记忆 (Mem0: {})", sessionId, mem0BaseUrl);
            } catch (Exception e) {
                log.warn("初始化长期记忆失败, 将跳过: {}", e.getMessage());
            }
        }

        ReActAgent agent = builder.build();

        return new AgentHandle(agent, hook, ltmEnabled, remoteExecTool);
    }

    /**
     * 构建 SkillBox: 加载内置技能 + 用户安装的技能, 并启用代码执行沙箱
     */
    private SkillBox buildSkillBox(Toolkit toolkit, Long userId) {
        SkillBox skillBox = new SkillBox(toolkit);

        // 1. 加载内置技能（从 classpath:skills/ 目录）
        try (ClasspathSkillRepository builtinRepo = new ClasspathSkillRepository("skills")) {
            List<AgentSkill> builtinSkills = builtinRepo.getAllSkills();
            // 获取用户启用的技能 ID 列表
            List<Skill> enabledSkills = userId != null ? skillService.getEnabledSkills(userId) : List.of();
            Set<String> enabledIds = enabledSkills.stream().map(Skill::getId).collect(Collectors.toSet());

            for (AgentSkill skill : builtinSkills) {
                // 仅注册用户启用的内置技能（如果没有偏好设置, 内置技能默认启用）
                if (enabledIds.isEmpty() || enabledIds.contains(skill.getName())) {
                    skillBox.registerSkill(skill);
                    log.debug("注册内置技能: {}", skill.getName());
                }
            }
        } catch (Exception e) {
            log.warn("加载内置技能失败: {}", e.getMessage());
        }

        // 2. 加载用户安装的技能（从文件系统）
        if (userId != null) {
            Path userSkillsDir = Path.of(skillsBaseDir, String.valueOf(userId));
            if (userSkillsDir.toFile().exists()) {
                try {
                    List<AgentSkill> userSkills;
                    try (FileSystemSkillRepository userRepo = new FileSystemSkillRepository(userSkillsDir)) {
                        userSkills = userRepo.getAllSkills();
                    }
                    List<Skill> enabledSkills = skillService.getEnabledSkills(userId);
                    Set<String> enabledIds = enabledSkills.stream().map(Skill::getId).collect(Collectors.toSet());

                    for (AgentSkill skill : userSkills) {
                        if (enabledIds.contains(skill.getName())) {
                            skillBox.registerSkill(skill);
                            log.debug("注册用户技能: {}", skill.getName());
                        }
                    }
                } catch (Exception e) {
                    log.warn("加载用户技能失败 (userId={}): {}", userId, e.getMessage());
                }
            }
        }

        return skillBox;
    }

    /**
     * 注册子代理: 将专用子代理作为工具注册到父 Agent 的 Toolkit。
     * 子代理在独立上下文中执行任务, 只返回精简结果, 减少父 Agent 上下文消耗。
     */
    private void registerSubAgents(Toolkit toolkit, DashScopeChatModel model, Long userId) {
        // ─── 联网子代理（依赖 Tavily API Key）───
        if (tavilyApiKey != null) {
            try {
                WebTools webTools = new WebTools(tavilyApiKey);
                Toolkit webToolkit = new Toolkit();
                webToolkit.registerTool(webTools);

                // web-researcher 子代理: 快速联网搜索
                String webPrompt = loadAgentPrompt("agents/web-researcher.ftl");
                toolkit.registration()
                        .subAgent(() -> ReActAgent.builder()
                                        .name("web-researcher")
                                        .sysPrompt(webPrompt)
                                        .model(model)
                                        .toolkit(webToolkit)
                                        .memory(new InMemoryMemory())
                                        .maxIters(5)
                                        .build(),
                                SubAgentConfig.builder()
                                        .toolName(ToolDefinitions.WEB_RESEARCHER)
                                        .description(ToolDefinitions.WEB_RESEARCHER_DESC)
                                        .build())
                        .apply();
                log.info("已注册子代理: web-researcher");

                // deep-researcher 子代理: 多轮深度研究
                String deepPrompt = loadAgentPrompt("agents/deep-researcher.ftl");
                toolkit.registration()
                        .subAgent(() -> ReActAgent.builder()
                                        .name("deep-researcher")
                                        .sysPrompt(deepPrompt)
                                        .model(model)
                                        .toolkit(webToolkit)
                                        .memory(new InMemoryMemory())
                                        .maxIters(15)
                                        .build(),
                                SubAgentConfig.builder()
                                        .toolName(ToolDefinitions.DEEP_RESEARCHER)
                                        .description(ToolDefinitions.DEEP_RESEARCHER_DESC)
                                        .build())
                        .apply();
                log.info("已注册子代理: deep-researcher");
            } catch (Exception e) {
                log.warn("注册联网子代理失败: {}", e.getMessage());
            }

            // batch_web_researcher 工具: 并行批量搜索
            try {
                DashScopeChatModel turboModel = chatModelFactory.createByModelName("qwen-turbo");
                BatchWebResearchTool batchTool = new BatchWebResearchTool(tavilyApiKey, turboModel);
                toolkit.registerTool(batchTool);
                log.info("已注册工具: batch_web_researcher");
            } catch (Exception e) {
                log.warn("注册 batch_web_researcher 失败: {}", e.getMessage());
            }
        } else {
            log.info("未配置 Tavily API Key, 跳过联网子代理注册");
        }

        // ─── 独立子代理（不依赖外部 API Key）───
        try {
            // translator 子代理: 纯 LLM 翻译
            String translatorPrompt = loadAgentPrompt("agents/translator.ftl");
            toolkit.registration()
                    .subAgent(() -> ReActAgent.builder()
                                    .name("translator")
                                    .sysPrompt(translatorPrompt)
                                    .model(model)
                                    .toolkit(new Toolkit())
                                    .memory(new InMemoryMemory())
                                    .maxIters(3)
                                    .build(),
                            SubAgentConfig.builder()
                                    .toolName(ToolDefinitions.TRANSLATOR)
                                    .description(ToolDefinitions.TRANSLATOR_DESC)
                                    .build())
                    .apply();
            log.info("已注册子代理: translator");

            // code-reviewer 子代理: 代码审查，使用代码专用模型
            DashScopeChatModel codeModel = chatModelFactory.createByModelName("qwen3-coder-plus");
            Toolkit codeToolkit = new Toolkit();
            codeToolkit.registerTool(new SimpleTools(fileRecordRepository, ossService, userId));

            String codeReviewerPrompt = loadAgentPrompt("agents/code-reviewer.ftl");
            toolkit.registration()
                    .subAgent(() -> ReActAgent.builder()
                                    .name("code-reviewer")
                                    .sysPrompt(codeReviewerPrompt)
                                    .model(codeModel)
                                    .toolkit(codeToolkit)
                                    .memory(new InMemoryMemory())
                                    .maxIters(5)
                                    .build(),
                            SubAgentConfig.builder()
                                    .toolName(ToolDefinitions.CODE_REVIEWER)
                                    .description(ToolDefinitions.CODE_REVIEWER_DESC)
                                    .build())
                    .apply();
            log.info("已注册子代理: code-reviewer (model=qwen3-coder-plus)");

            // summarizer 子代理: 长文本摘要
            String summarizerPrompt = loadAgentPrompt("agents/summarizer.ftl");
            toolkit.registration()
                    .subAgent(() -> ReActAgent.builder()
                                    .name("summarizer")
                                    .sysPrompt(summarizerPrompt)
                                    .model(model)
                                    .toolkit(new Toolkit())
                                    .memory(new InMemoryMemory())
                                    .maxIters(3)
                                    .build(),
                            SubAgentConfig.builder()
                                    .toolName(ToolDefinitions.SUMMARIZER)
                                    .description(ToolDefinitions.SUMMARIZER_DESC)
                                    .build())
                    .apply();
            log.info("已注册子代理: summarizer");

            // planner 子代理: 任务规划
            String plannerPrompt = loadAgentPrompt("agents/planner.ftl");
            toolkit.registration()
                    .subAgent(() -> ReActAgent.builder()
                                    .name("planner")
                                    .sysPrompt(plannerPrompt)
                                    .model(model)
                                    .toolkit(new Toolkit())
                                    .memory(new InMemoryMemory())
                                    .maxIters(3)
                                    .build(),
                            SubAgentConfig.builder()
                                    .toolName(ToolDefinitions.PLANNER)
                                    .description(ToolDefinitions.PLANNER_DESC)
                                    .build())
                    .apply();
            log.info("已注册子代理: planner");
        } catch (Exception e) {
            log.warn("注册独立子代理失败: {}", e.getMessage());
        }

        // ─── 动态子代理工具 ───
        try {
            // 构建工具池: 子代理可选的工具实例
            Map<String, Object> toolPool = new LinkedHashMap<>();
            SimpleTools simpleToolsForPool = new SimpleTools(fileRecordRepository, ossService, userId);
            toolPool.put(ToolDefinitions.GET_CURRENT_TIME, simpleToolsForPool);
            toolPool.put(ToolDefinitions.CALCULATE, simpleToolsForPool);
            toolPool.put(ToolDefinitions.READ_FILE, simpleToolsForPool);
            toolPool.put(ToolDefinitions.API_CALL, new ApiCallTool());
            toolPool.put(ToolDefinitions.IP_LOCATION, new IpLocationTool());
            if (tavilyApiKey != null) {
                WebTools webToolsForPool = new WebTools(tavilyApiKey);
                toolPool.put(ToolDefinitions.WEB_SEARCH, webToolsForPool);
                toolPool.put(ToolDefinitions.URL_FETCH, webToolsForPool);
            }

            DynamicAgentTool dynamicAgentTool = new DynamicAgentTool(model, toolPool);
            toolkit.registerTool(dynamicAgentTool);
            log.info("已注册工具: create_agent (可用工具池: {})", toolPool.keySet());
        } catch (Exception e) {
            log.warn("注册 create_agent 工具失败: {}", e.getMessage());
        }
    }

    /**
     * 从 classpath 加载子代理 prompt 文件（.ftl 纯文本模板）。
     */
    private String loadAgentPrompt(String resourcePath) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                log.warn("子代理 prompt 文件未找到: {}", resourcePath);
                return "";
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            log.warn("加载子代理 prompt 失败: {}", e.getMessage());
            return "";
        }
    }
}
