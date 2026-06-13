package top.jionjion.agentdesk.agent.tool;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostCallEvent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 动态子代理工具。
 * <p>
 * 允许主 Agent 在运行时动态创建临时子代理——自定义 system prompt 和工具组合，
 * 执行任务后返回结果。子代理执行完毕后即销毁，不保留状态。
 * <p>
 * 可选工具从固定池中选取（安全边界），防止递归调用或访问敏感工具。
 *
 * @author Jion
 */
public class DynamicAgentTool {

    private static final Logger log = LoggerFactory.getLogger(DynamicAgentTool.class);

    private static final int DEFAULT_MAX_ITERS = 5;
    private static final int MAX_ITERS_LIMIT = 10;
    private static final int MAX_OUTPUT_LENGTH = 2000;
    private static final long EXECUTION_TIMEOUT_SECONDS = 120;

    private final DashScopeChatModel model;
    private final Map<String, Object> toolPool;
    private final ExecutorService executor;

    /**
     * @param model     LLM 模型（子代理使用）
     * @param toolInstances 可用工具实例映射（工具名 → 工具对象）
     */
    public DynamicAgentTool(DashScopeChatModel model, Map<String, Object> toolInstances) {
        this.model = model;
        this.toolPool = new LinkedHashMap<>(toolInstances);
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "dynamic-agent-worker");
            t.setDaemon(true);
            return t;
        });
    }

    @Tool(name = ToolDefinitions.CREATE_AGENT, description = ToolDefinitions.CREATE_AGENT_DESC)
    public String createAgent(
            @ToolParam(name = "name", description = "子代理名称，用于日志追踪") String name,
            @ToolParam(name = "sys_prompt", description = "系统提示词，定义子代理的角色、目标和行为约束") String sysPrompt,
            @ToolParam(name = "tools", description = "逗号分隔的工具名列表。可选: web_search, url_fetch, get_current_time, calculate, read_file, api_call, ip_location。留空则不使用工具。") String tools,
            @ToolParam(name = "task", description = "要执行的具体任务描述") String task,
            @ToolParam(name = "max_iters", description = "最大迭代次数（默认5，上限10）。纯文本生成设为1即可。") Integer maxIters) {

        long startTime = System.currentTimeMillis();

        // 参数校验
        if (name == null || name.isBlank()) {
            name = "dynamic-agent";
        }
        if (sysPrompt == null || sysPrompt.isBlank()) {
            return "错误: sys_prompt 不能为空，请提供子代理的角色定义。";
        }
        if (task == null || task.isBlank()) {
            return "错误: task 不能为空，请提供要执行的任务。";
        }

        int iters = (maxIters == null || maxIters <= 0) ? DEFAULT_MAX_ITERS : Math.min(maxIters, MAX_ITERS_LIMIT);

        log.info("创建动态子代理 [{}], 工具: [{}], maxIters: {}", name, tools, iters);

        // 构建 Toolkit
        Toolkit subToolkit = new Toolkit();
        if (tools != null && !tools.isBlank()) {
            List<String> requestedTools = Arrays.stream(tools.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());

            List<String> registered = new ArrayList<>();
            List<String> unknown = new ArrayList<>();
            Set<Object> registeredInstances = new HashSet<>();

            for (String toolName : requestedTools) {
                Object toolInstance = toolPool.get(toolName);
                if (toolInstance != null) {
                    // 同一实例只注册一次（避免重复注册同一对象的所有 @Tool 方法）
                    if (registeredInstances.add(toolInstance)) {
                        subToolkit.registerTool(toolInstance);
                    }
                    registered.add(toolName);
                } else {
                    unknown.add(toolName);
                }
            }

            if (!unknown.isEmpty()) {
                log.warn("动态子代理 [{}] 请求了未知工具: {}, 可用: {}", name, unknown, toolPool.keySet());
            }
            if (!registered.isEmpty()) {
                log.info("动态子代理 [{}] 已注册工具: {}", name, registered);
            }
        }

        // 构建子代理
        ReplyCapturingHook hook = new ReplyCapturingHook();
        ReActAgent subAgent = ReActAgent.builder()
                .name(name)
                .sysPrompt(sysPrompt)
                .model(model)
                .toolkit(subToolkit)
                .memory(new InMemoryMemory())
                .hook(hook)
                .maxIters(iters)
                .build();

        // 执行任务（带超时）
        Msg userMsg = Msg.builder().textContent(task).build();
        try {
            CompletableFuture<Void> future = CompletableFuture.runAsync(
                    () -> subAgent.stream(userMsg).blockLast(), executor);
            future.get(EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("动态子代理 [{}] 执行超时 ({}s)", name, EXECUTION_TIMEOUT_SECONDS);
            return "子代理执行超时（" + EXECUTION_TIMEOUT_SECONDS + "秒），任务可能过于复杂。请简化任务或增加迭代次数。";
        } catch (Exception e) {
            log.error("动态子代理 [{}] 执行异常: {}", name, e.getMessage());
            return "子代理执行失败: " + e.getMessage();
        }

        // 获取结果
        String reply = hook.getReply();
        long elapsed = System.currentTimeMillis() - startTime;
        log.info("动态子代理 [{}] 执行完成, 耗时: {}ms", name, elapsed);

        if (reply == null || reply.isBlank()) {
            return "子代理未产生有效输出。请检查 sys_prompt 和 task 是否清晰。";
        }

        // 截断过长输出
        if (reply.length() > MAX_OUTPUT_LENGTH) {
            reply = reply.substring(0, MAX_OUTPUT_LENGTH) + "\n...(输出已截断，共 " + reply.length() + " 字)";
        }

        return reply;
    }

    /**
     * 获取可用工具列表（供主 Agent 参考）
     */
    public Set<String> getAvailableTools() {
        return Collections.unmodifiableSet(toolPool.keySet());
    }

    // ─── 内部 Hook：仅捕获最终回复 ───

    private static class ReplyCapturingHook implements Hook {

        private volatile String reply;

        @Override
        public <T extends HookEvent> Mono<T> onEvent(T event) {
            if (event instanceof PostCallEvent e) {
                var finalMsg = e.getFinalMessage();
                if (finalMsg != null) {
                    reply = finalMsg.getTextContent();
                }
            }
            return Mono.just(event);
        }

        public String getReply() {
            return reply;
        }
    }
}
