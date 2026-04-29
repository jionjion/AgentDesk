package top.jionjion.agentdesk.agent;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.memory.LongTermMemoryMode;
import io.agentscope.core.memory.mem0.Mem0ApiType;
import io.agentscope.core.memory.mem0.Mem0LongTermMemory;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.tool.Toolkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import top.jionjion.agentdesk.dto.MemorySettingsDto;
import top.jionjion.agentdesk.dto.ModelSettingsDto;
import top.jionjion.agentdesk.entity.Skill;
import top.jionjion.agentdesk.repository.FileRecordRepository;
import top.jionjion.agentdesk.security.UserContext;
import top.jionjion.agentdesk.service.OssService;
import top.jionjion.agentdesk.service.SettingsService;
import top.jionjion.agentdesk.service.SkillService;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent 工厂: 为每个会话创建独立的 Agent 实例
 * <p>
 * 子代理由数据库中启用的技能动态注册, 不再硬编码。
 *
 * @author Jion
 */
@Component
public class AgentFactory {

    private static final Logger log = LoggerFactory.getLogger(AgentFactory.class);

    private static final String SYS_PROMPT_TEMPLATE = """
            你是一个名为 Assistant 的智能助手协调者。你可以直接回答简单问题，也可以将复杂任务委派给专业的子助手。

            %s

            当用户上传了文件时，消息中会包含文件的元信息 (文件名、大小、类型、fileId)。
            对于文件相关任务，请将 fileId 传递给相应的子助手。

            你也可以直接使用 get_current_time、calculate、read_file 等工具处理简单任务。
            不要猜测文件内容，请先调用 read_file 获取实际内容。

            请用中文回答。
            """;

    private static final String FALLBACK_SYS_PROMPT = SYS_PROMPT_TEMPLATE.formatted("你暂时没有可调用的专家，请直接回答用户问题。");

    private final ChatModelFactory chatModelFactory;
    private final FileRecordRepository fileRecordRepository;
    private final OssService ossService;
    private final SettingsService settingsService;
    private final SkillService skillService;
    private final String mem0BaseUrl;
    private final String mem0ApiKey;

    public AgentFactory(ChatModelFactory chatModelFactory,
                        FileRecordRepository fileRecordRepository,
                        OssService ossService,
                        SettingsService settingsService,
                        SkillService skillService,
                        @Value("${agentdesk.mem0.base-url}") String mem0BaseUrl,
                        @Value("${agentdesk.mem0.api-key:}") String mem0ApiKey) {
        this.chatModelFactory = chatModelFactory;
        this.fileRecordRepository = fileRecordRepository;
        this.ossService = ossService;
        this.settingsService = settingsService;
        this.skillService = skillService;
        this.mem0BaseUrl = mem0BaseUrl.endsWith("/") ? mem0BaseUrl.substring(0, mem0BaseUrl.length() - 1) : mem0BaseUrl;
        this.mem0ApiKey = (mem0ApiKey == null || mem0ApiKey.isBlank()) ? null : mem0ApiKey;
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

        // 读取用户模型配置 + API Key
        ModelSettingsDto ms = ModelSettingsDto.defaults();
        MemorySettingsDto memSettings = MemorySettingsDto.defaults();
        String userApiKey = null;
        String sysPrompt;
        List<Skill> enabledSkills = List.of();
        Long userId = null;

        if (UserContext.isAuthenticated()) {
            userId = UserContext.getUserId();
            ms = settingsService.getModelSettings(userId);
            userApiKey = settingsService.getDashScopeApiKey(userId);
            enabledSkills = skillService.getEnabledSkills(userId);
            memSettings = settingsService.getMemorySettings(userId);
        }

        // 通过工厂创建模型（自动 fallback 到系统默认 Key）
        DashScopeChatModel model = chatModelFactory.create(ms, userApiKey);

        // 动态注册子代理（基于启用的技能）
        registerSkillSubAgents(toolkit, model, enabledSkills);

        // 构建系统提示词
        sysPrompt = buildSystemPrompt(enabledSkills);

        // 用户自定义系统提示词优先，但追加技能列表
        if (ms.systemPrompt() != null && !ms.systemPrompt().isBlank()) {
            String skillSection = buildSkillSection(enabledSkills);
            sysPrompt = ms.systemPrompt() + (skillSection.isEmpty() ? "" : "\n\n" + skillSection);
        }

        // 构建 ReActAgent
        ReActAgent.Builder builder = ReActAgent.builder()
                .name("assistant-" + sessionId)
                .sysPrompt(sysPrompt)
                .model(model)
                .toolkit(toolkit)
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
     * 根据启用的技能动态注册子代理
     */
    private void registerSkillSubAgents(Toolkit toolkit, DashScopeChatModel model, List<Skill> skills) {
        for (Skill skill : skills) {
            toolkit.registration()
                    .subAgent(() -> {
                        ReActAgent.Builder builder = ReActAgent.builder()
                                .name(skill.getId().replace("-", "_"))
                                .description(skill.getDescription())
                                .sysPrompt(skill.getSysPrompt())
                                .model(model)
                                .maxIters(skill.getMaxIters());

                        // 如果技能配置了工具，创建独立的 Toolkit
                        if (skill.getTools() != null && !skill.getTools().isEmpty()) {
                            Toolkit subToolkit = new Toolkit();
                            for (String toolName : skill.getTools()) {
                                Object toolInstance = resolveToolInstance(toolName);
                                if (toolInstance != null) {
                                    subToolkit.registerTool(toolInstance);
                                }
                            }
                            builder.toolkit(subToolkit);
                        }

                        return builder.build();
                    })
                    .apply();
        }
    }

    /**
     * 根据工具类名创建工具实例
     */
    private Object resolveToolInstance(String toolName) {
        return switch (toolName) {
            case "FileTools" -> new FileTools(fileRecordRepository, ossService);
            case "CalculateTools" -> new CalculateTools();
            default -> {
                log.warn("未知的工具类名: {}, 已跳过", toolName);
                yield null;
            }
        };
    }

    /**
     * 基于启用的技能构建完整的系统提示词
     */
    private String buildSystemPrompt(List<Skill> skills) {
        if (skills.isEmpty()) {
            return FALLBACK_SYS_PROMPT;
        }
        String skillSection = buildSkillSection(skills);
        return SYS_PROMPT_TEMPLATE.formatted(skillSection);
    }

    /**
     * 构建技能列表描述段落
     */
    private String buildSkillSection(List<Skill> skills) {
        if (skills.isEmpty()) {
            return "";
        }
        String skillList = skills.stream()
                .map(s -> "- call_" + s.getId().replace("-", "_") + ": " + s.getDescription())
                .collect(Collectors.joining("\n"));
        return "你有以下专家可以调用：\n" + skillList;
    }
}
