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
import io.agentscope.core.tool.coding.ShellCommandTool;
import io.agentscope.core.tool.subagent.SubAgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import top.jionjion.agentdesk.agent.hook.SseStreamingHook;
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Agent 工厂: 为每个会话创建独立的 Agent 实例
 * <p>
 * 使用 AgentScope SkillBox 系统进行技能管理:
 * - 内置技能通过 ClasspathSkillRepository 加载
 * - 用户安装的技能通过 FileSystemSkillRepository 加载
 * - 启用沙箱化代码执行 (ShellCommandTool)
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

            你也可以直接使用 get_current_time、calculate、read_file 等工具处理简单任务。
            不要猜测文件内容，请先调用 read_file 获取实际内容。

            当用户需要搜索互联网、查询网页内容或获取最新资讯时，使用 web_researcher 子代理。
            当用户提出复杂问题需要多角度调研、交叉验证时，使用 deep_researcher 子代理。
            当用户需要翻译文本或文档时，使用 translator 子代理。
            当用户提交代码要求审查、或需要代码质量分析时，使用 code_reviewer 子代理。
            传入清晰的任务描述即可，子代理会返回精简的结果。

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
    private final String mem0BaseUrl;
    private final String mem0ApiKey;
    private final String skillsBaseDir;
    private final String codeExecutionWorkDir;
    private final boolean codeExecutionEnabled;
    private final Set<String> allowedCommands;
    private final String tavilyApiKey;

    public AgentFactory(ChatModelFactory chatModelFactory,
                        FileRecordRepository fileRecordRepository,
                        OssService ossService,
                        SettingsService settingsService,
                        SkillService skillService,
                        McpServerService mcpServerService,
                        McpConnectionManager mcpConnectionManager,
                        @Value("${agentdesk.mem0.base-url}") String mem0BaseUrl,
                        @Value("${agentdesk.mem0.api-key:}") String mem0ApiKey,
                        @Value("${agentdesk.skills.base-dir}") String skillsBaseDir,
                        @Value("${agentdesk.skills.code-execution.work-dir}") String codeExecutionWorkDir,
                        @Value("${agentdesk.skills.code-execution.enabled:true}") boolean codeExecutionEnabled,
                        @Value("${agentdesk.skills.code-execution.allowed-commands:python3,python,node}") String allowedCommandsStr,
                        @Value("${agentdesk.tools.web-search.api-key:}") String tavilyApiKey) {
        this.chatModelFactory = chatModelFactory;
        this.fileRecordRepository = fileRecordRepository;
        this.ossService = ossService;
        this.settingsService = settingsService;
        this.skillService = skillService;
        this.mcpServerService = mcpServerService;
        this.mcpConnectionManager = mcpConnectionManager;
        this.mem0BaseUrl = mem0BaseUrl.endsWith("/") ? mem0BaseUrl.substring(0, mem0BaseUrl.length() - 1) : mem0BaseUrl;
        this.mem0ApiKey = (mem0ApiKey == null || mem0ApiKey.isBlank()) ? null : mem0ApiKey;
        this.skillsBaseDir = skillsBaseDir;
        this.codeExecutionWorkDir = codeExecutionWorkDir;
        this.codeExecutionEnabled = codeExecutionEnabled;
        this.allowedCommands = Arrays.stream(allowedCommandsStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        this.tavilyApiKey = (tavilyApiKey == null || tavilyApiKey.isBlank()) ? null : tavilyApiKey;
    }

    /**
     * 创建一个新的 Agent 实例（含 SkillBox、Toolkit 和 Hook）
     */
    public AgentHandle createAgent(String sessionId) {
        // 每个 Agent 独立的 Toolkit（Toolkit 有状态, 不可共享）
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new SimpleTools(fileRecordRepository, ossService));

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

        // 通过工厂创建模型（自动 fallback 到系统默认 Key）
        DashScopeChatModel model = chatModelFactory.create(ms, userApiKey);

        // 构建 SkillBox（核心变更: 从 sub-agent 模式迁移到 SkillBox 模式）
        SkillBox skillBox = buildSkillBox(toolkit, userId);

        // MCP 服务器集成: 连接已启用的 MCP 服务器并注册工具
        if (userId != null) {
            List<McpServer> mcpServers = mcpServerService.getEnabledServers(userId);
            if (!mcpServers.isEmpty()) {
                mcpConnectionManager.connectAndRegister(toolkit, mcpServers);
                log.info("已为会话 {} 注册 {} 个 MCP 服务器", sessionId, mcpServers.size());
            }
        }

        // 子代理集成: 注册 web-researcher 子代理（通过 Agent as Tool 模式）
        registerSubAgents(toolkit, model);

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
                .maxIters(10);

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

        return new AgentHandle(agent, hook, ltmEnabled);
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
                    FileSystemSkillRepository userRepo = new FileSystemSkillRepository(userSkillsDir);
                    List<AgentSkill> userSkills = userRepo.getAllSkills();
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

        // 3. 启用代码执行沙箱（受限的 Shell 命令）
        if (codeExecutionEnabled) {
            try {
                skillBox.codeExecution()
                        .workDir(codeExecutionWorkDir)
                        .withShell(new ShellCommandTool(null, allowedCommands, null))
                        .withRead()
                        .withWrite()
                        .enable();
                log.info("技能代码执行沙箱已启用, workDir={}, 允许命令={}", codeExecutionWorkDir, allowedCommands);
            } catch (Exception e) {
                log.warn("启用代码执行沙箱失败: {}", e.getMessage());
            }
        }

        return skillBox;
    }

    /**
     * 注册子代理: 将专用子代理作为工具注册到父 Agent 的 Toolkit。
     * 子代理在独立上下文中执行任务, 只返回精简结果, 减少父 Agent 上下文消耗。
     */
    private void registerSubAgents(Toolkit toolkit, DashScopeChatModel model) {
        if (tavilyApiKey == null) {
            log.info("未配置 Tavily API Key, 跳过联网子代理注册");
            return;
        }

        try {
            // 共用的 Web 工具集
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

            // translator 子代理: 纯 LLM 翻译，无需额外工具
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
            codeToolkit.registerTool(new SimpleTools(fileRecordRepository, ossService));

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

        } catch (Exception e) {
            log.warn("注册子代理失败: {}", e.getMessage());
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
