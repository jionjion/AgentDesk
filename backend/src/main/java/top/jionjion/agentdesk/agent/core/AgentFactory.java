package top.jionjion.agentdesk.agent.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.skill.repository.ClasspathSkillRepository;
import io.agentscope.core.skill.repository.FileSystemSkillRepository;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.permission.PermissionRule;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.subagent.SubagentDeclaration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import top.jionjion.agentdesk.agent.runtime.AgentEventBridge;
import top.jionjion.agentdesk.agent.tool.ApiCallTool;
import top.jionjion.agentdesk.agent.tool.BatchWebResearchTool;
import top.jionjion.agentdesk.agent.tool.CommandRiskClassifier;
import top.jionjion.agentdesk.agent.tool.IpLocationTool;
import top.jionjion.agentdesk.agent.tool.RemoteExecTool;
import top.jionjion.agentdesk.agent.tool.SandboxExecTool;
import top.jionjion.agentdesk.agent.tool.SimpleTools;
import top.jionjion.agentdesk.agent.tool.ToolDefinitions;
import top.jionjion.agentdesk.agent.tool.WebTools;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Builds the AgentDesk primary Harness agent and its expert team. */
@Component
public class AgentFactory {

    private static final Logger log = LoggerFactory.getLogger(AgentFactory.class);

    private static final String SYS_PROMPT = """
            你是 AgentDesk 的首席助理，也是用户电脑上的专家团协调者。

            简单问题直接回答；复杂任务先澄清目标、维护任务清单，再委派给合适的专家。
            当用户上传文件时，消息会包含文件元信息与 fileId。不要猜测文件内容，先使用工具读取。
            联网研究交给 researcher；代码实现与审查交给 software-engineer 或 code-reviewer；
            数据分析交给 data-analyst；写作交给 writer；本机操作使用受审批保护的 remote_exec。

            在调用会产生外部影响的工具前，用一句话说明将执行什么。工具连续失败两次后停止重试，
            说明失败原因并给出可行替代方案。最终回答应汇总专家结论、已完成动作和仍需用户决策的事项。
            请使用中文回答。
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
    private final AgentStateStore stateStore;
    private final ObjectMapper objectMapper;
    private final String skillsBaseDir;
    private final String workspaceBaseDir;
    private final String tavilyApiKey;
    private final boolean remoteExecEnabled;

    public AgentFactory(
            ChatModelFactory chatModelFactory,
            FileRecordRepository fileRecordRepository,
            OssService ossService,
            SettingsService settingsService,
            SkillService skillService,
            McpServerService mcpServerService,
            McpConnectionManager mcpConnectionManager,
            RemoteExecBridge remoteExecBridge,
            CommandRiskClassifier commandRiskClassifier,
            AgentStateStore stateStore,
            ObjectMapper objectMapper,
            @Value("${agentdesk.skills.base-dir}") String skillsBaseDir,
            @Value("${agentdesk.workspace.base-dir:${user.home}/.agentdesk/workspace}") String workspaceBaseDir,
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
        this.stateStore = stateStore;
        this.objectMapper = objectMapper;
        this.skillsBaseDir = skillsBaseDir;
        this.workspaceBaseDir = workspaceBaseDir;
        this.tavilyApiKey = blankToNull(tavilyApiKey);
        this.remoteExecEnabled = remoteExecEnabled;
    }

    public AgentHandle createAgent(String sessionId) {
        Long userId = UserContext.isAuthenticated() ? UserContext.getUserId() : null;
        ModelSettingsDto settings = userId != null
                ? settingsService.getModelSettings(userId)
                : ModelSettingsDto.defaults();
        String apiKey = userId != null ? settingsService.getDashScopeApiKey(userId) : null;
        DashScopeChatModel model = chatModelFactory.create(settings, apiKey);

        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new ApiCallTool());
        toolkit.registerTool(new IpLocationTool());
        toolkit.registerTool(new SimpleTools(fileRecordRepository, ossService, userId));

        registerMcpTools(toolkit, userId, sessionId);
        RemoteExecTool remoteExecTool = registerDesktopTools(toolkit, userId, sessionId);
        registerResearchTool(toolkit);

        List<Skill> enabledSkills = userId == null ? List.of() : skillService.getEnabledSkills(userId);

        String basePrompt = settings.systemPrompt() != null && !settings.systemPrompt().isBlank()
                ? settings.systemPrompt()
                : SYS_PROMPT;
        String prompt = appendEnabledPromptSkills(basePrompt, enabledSkills);
        Path workspace = Path.of(workspaceBaseDir, userId == null ? "anonymous" : userId.toString());
        try {
            Files.createDirectories(workspace);
        } catch (IOException ex) {
            throw new IllegalStateException("无法创建 Agent 工作区: " + workspace, ex);
        }

        HarnessAgent.Builder builder = HarnessAgent.builder()
                .name("assistant")
                .agentId("assistant")
                .description("AgentDesk 首席助理与专家团队协调者")
                .sysPrompt(prompt)
                .model(model)
                .modelResolver(chatModelFactory::createByModelName)
                .toolkit(toolkit)
                .permissionContext(buildPermissionContext(toolkit))
                .stateStore(stateStore)
                .workspace(workspace)
                .enableTaskList()
                .enablePlanMode()
                .disableShellTool()
                .maxIters(30);

        registerSkillRepositories(builder, userId, enabledSkills);
        registerExpertTeam(builder);

        log.info("AgentScope v2 Harness ready: session={}, user={}, tools={}",
                sessionId, userId, toolkit.getToolNames());
        return new AgentHandle(builder.build(), new AgentEventBridge(objectMapper), remoteExecTool);
    }

    private void registerMcpTools(Toolkit toolkit, Long userId, String sessionId) {
        if (userId == null) {
            return;
        }
        List<McpServer> servers = mcpServerService.getEnabledServers(userId);
        if (servers.isEmpty()) {
            return;
        }
        Map<Long, Boolean> results = mcpConnectionManager.connectAndRegister(toolkit, servers);
        mcpServerService.applyConnectionResults(results);
        log.info("已为会话 {} 注册 {} 个 MCP 服务器", sessionId, servers.size());
    }

    private RemoteExecTool registerDesktopTools(Toolkit toolkit, Long userId, String sessionId) {
        if (!remoteExecEnabled || userId == null) {
            return null;
        }
        RemoteExecTool remote = new RemoteExecTool(
                remoteExecBridge, commandRiskClassifier, userId, sessionId);
        toolkit.registerTool(remote);
        toolkit.registerTool(new SandboxExecTool(remoteExecBridge, userId, sessionId));
        return remote;
    }

    private void registerResearchTool(Toolkit toolkit) {
        if (tavilyApiKey == null) {
            log.warn("TAVILY_API_KEY 未配置，联网研究工具不会注册");
            return;
        }
        toolkit.registerTool(new WebTools(tavilyApiKey));
        toolkit.registerTool(new BatchWebResearchTool(
                tavilyApiKey, chatModelFactory.createByModelName("qwen-turbo")));
    }

    private void registerSkillRepositories(HarnessAgent.Builder builder, Long userId,
                                           List<Skill> enabledSkills) {
        Set<String> enabledSkillNames = enabledSkills.stream()
                .map(Skill::getId)
                .collect(Collectors.toUnmodifiableSet());
        try {
            var repository = new ClasspathSkillRepository("skills", "agentdesk-builtin");
            builder.skillRepository(userId == null
                    ? repository
                    : new FilteredSkillRepository(repository, enabledSkillNames));
        } catch (IOException ex) {
            log.warn("加载内置技能失败", ex);
        }
        if (userId == null) {
            return;
        }
        Path userSkills = Path.of(skillsBaseDir, userId.toString());
        if (Files.isDirectory(userSkills)) {
            var repository = new FileSystemSkillRepository(
                    userSkills, false, "agentdesk-user-" + userId);
            builder.skillRepository(new FilteredSkillRepository(repository, enabledSkillNames));
        }
    }

    private void registerExpertTeam(HarnessAgent.Builder builder) {
        builder.subagent(expert("researcher", "联网研究、交叉验证并整理来源",
                "agents/deep-researcher.ftl", 15, false,
                ToolDefinitions.WEB_SEARCH, ToolDefinitions.URL_FETCH,
                ToolDefinitions.BATCH_WEB_RESEARCHER, ToolDefinitions.READ_FILE,
                ToolDefinitions.GET_CURRENT_TIME, ToolDefinitions.CALCULATE));
        builder.subagent(expert("software-engineer", "实现、调试和验证软件变更",
                "agents/software-engineer.ftl", 20, false,
                ToolDefinitions.REMOTE_EXEC, ToolDefinitions.READ_FILE,
                ToolDefinitions.SANDBOX_EXEC, ToolDefinitions.CALCULATE));
        builder.subagent(expert("code-reviewer", "独立审查代码的正确性、安全性与可维护性",
                "agents/code-reviewer.ftl", 10, false,
                ToolDefinitions.REMOTE_EXEC, ToolDefinitions.READ_FILE));
        builder.subagent(expert("data-analyst", "执行数据清洗、统计分析并解释结果",
                "agents/data-analyst.ftl", 12, false,
                ToolDefinitions.SANDBOX_EXEC, ToolDefinitions.REMOTE_EXEC,
                ToolDefinitions.READ_FILE, ToolDefinitions.CALCULATE));
        builder.subagent(expert("writer", "撰写、编辑和结构化交付文档",
                "agents/writer.ftl", 8, false,
                ToolDefinitions.READ_FILE));
        builder.subagent(expert("knowledge-curator", "整理知识、消歧去重并沉淀可检索资料",
                "agents/knowledge-curator.ftl", 10, false,
                ToolDefinitions.READ_FILE, ToolDefinitions.WEB_SEARCH,
                ToolDefinitions.URL_FETCH));
        builder.subagent(expert("system-operator", "在明确授权下执行本机操作并报告影响",
                "agents/system-operator.ftl", 12, false,
                ToolDefinitions.REMOTE_EXEC, ToolDefinitions.SANDBOX_EXEC));
    }

    private SubagentDeclaration expert(
            String name, String description, String promptResource, int steps,
            boolean persistent, String... tools) {
        return SubagentDeclaration.builder()
                .name(name)
                .description(description)
                .inlineAgentsBody(loadPrompt(promptResource))
                .steps(steps)
                .persistSession(persistent)
                .inheritParentPermissions(true)
                .tools(List.of(tools))
                .build();
    }

    private PermissionContextState buildPermissionContext(Toolkit toolkit) {
        var context = PermissionContextState.builder().mode(PermissionMode.DONT_ASK);
        context.addDenyRule("execute", new PermissionRule(
                "execute", null, PermissionBehavior.DENY, "agentdesk"));

        Set<String> allowedTools = new LinkedHashSet<>(toolkit.getToolNames());
        allowedTools.addAll(List.of(
                "read_file", "write_file", "edit_file", "grep_files", "glob_files", "list_files",
                "session_search", "session_list", "session_history",
                "memory_get", "memory_search", "memory_save",
                "agent_spawn", "agent_send", "agent_list",
                "task_output", "task_cancel", "task_list",
                "plan_enter", "plan_write", "plan_exit",
                "load_skill_through_path", "read_skill", "use_skill"));
        for (String toolName : allowedTools) {
            context.addAllowRule(toolName, new PermissionRule(
                    toolName, null, PermissionBehavior.ALLOW, "agentdesk"));
        }
        return context.build();
    }

    private String appendEnabledPromptSkills(String basePrompt, List<Skill> enabledSkills) {
        String promptSkills = enabledSkills.stream()
                .filter(skill -> !"package".equals(skill.getSkillType()))
                .map(Skill::getSysPrompt)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining("\n\n---\n\n"));
        if (promptSkills.isBlank()) {
            return basePrompt;
        }
        return basePrompt + "\n\n[用户已启用的技能指令]\n" + promptSkills;
    }

    private String loadPrompt(String resourcePath) {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) {
                return "你是专家团队成员。只处理被委派的任务，给出可验证、简洁的结论。";
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException ex) {
            throw new IllegalStateException("无法加载专家提示词: " + resourcePath, ex);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
