package top.jionjion.agentdesk.agent.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.jionjion.agentdesk.agent.exec.ClientExecException;
import top.jionjion.agentdesk.agent.exec.ClientExecutor;
import top.jionjion.agentdesk.agent.runtime.ProjectRuntimeContext;
import top.jionjion.agentdesk.websocket.dto.CommandResult;

/**
 * 远程命令执行工具: Agent 通过此工具在用户的本地机器上执行 shell 命令。
 * <p>
 * 命令通过 WebSocket 发送到客户端执行, 根据风险等级决定是否需要用户确认。
 * 低风险命令（如 ls, cat, git status）自动执行, 高风险命令需要用户在客户端确认。
 * <p>
 * 工作目录规则 (见开发计划 9.4):
 * <ul>
 *   <li>working_dir 是逐次调用参数, 不跨工具调用保持</li>
 *   <li>默认工作目录来自本次调用注入的 {@link ProjectRuntimeContext}</li>
 *   <li>不拦截裸 cd、不伪造持久目录状态</li>
 * </ul>
 *
 * @author Jion
 */
public class RemoteExecTool {

    private static final Logger log = LoggerFactory.getLogger(RemoteExecTool.class);

    private static final String PLATFORM_WIN = "win";
    private static final String PLATFORM_DARWIN = "darwin";
    private static final String PLATFORM_MAC = "mac";
    private static final String PLATFORM_LINUX = "linux";

    private final ClientExecutor bridge;
    private final CommandRiskClassifier riskClassifier;
    private final Long userId;
    private final String sessionId;

    public RemoteExecTool(ClientExecutor bridge, CommandRiskClassifier riskClassifier,
                          Long userId, String sessionId) {
        this.bridge = bridge;
        this.riskClassifier = riskClassifier;
        this.userId = userId;
        this.sessionId = sessionId;
    }

    @Tool(name = ToolDefinitions.REMOTE_EXEC, description = ToolDefinitions.REMOTE_EXEC_DESC)
    public String execute(
            @ToolParam(name = "command", description = "要在用户本地机器上执行的 shell 命令。注意: 必须使用与用户操作系统匹配的命令语法") String command,
            @ToolParam(name = "working_dir", description = "本次命令的工作目录, 可选。相对路径按当前项目根解析; 不传则使用项目根目录。该参数不跨调用保持", required = false) String workingDir,
            ProjectRuntimeContext projectContext
    ) {
        if (command == null || command.isBlank()) {
            return "错误: 命令不能为空";
        }

        // 检查客户端连接
        if (!bridge.isConnected(userId)) {
            return "错误: 用户的桌面客户端未连接, 无法执行远程命令。请提示用户启动桌面客户端并确保远程执行功能已开启。";
        }

        // 解析工作目录: 显式传入(相对路径按项目根解析) > 项目默认 cwd > 客户端默认
        String effectiveDir = resolveWorkingDir(workingDir, projectContext);

        // 获取客户端平台信息
        String platform = bridge.getClientPlatform(userId);
        String osHint = resolveOsHint(platform);

        // 风险分级
        String riskLevel = riskClassifier.classify(command);
        log.info("远程执行命令: [{}] {} (userId={}, session={}, platform={}, cwd={})",
                riskLevel, command, userId, sessionId, platform, effectiveDir);

        try {
            CommandResult result = bridge.executeCommand(userId, sessionId, command, effectiveDir, riskLevel);
            return formatResult(command, result, osHint);
        } catch (ClientExecException e) {
            return "远程执行失败: " + e.getMessage();
        }
    }

    /**
     * 解析本次调用的有效工作目录。
     * 显式传入优先 (相对路径以项目根为基准); 其次项目默认 cwd; 均无时为 null (客户端使用默认)。
     */
    private String resolveWorkingDir(String workingDir, ProjectRuntimeContext projectContext) {
        String projectRoot = projectContext != null && projectContext.runtimeOnline()
                ? projectContext.rootPath() : null;
        if (workingDir != null && !workingDir.isBlank()) {
            String dir = workingDir.trim();
            if (projectRoot != null && isRelative(dir)) {
                return joinPath(projectRoot, dir);
            }
            return dir;
        }
        if (projectContext != null && projectContext.runtimeOnline()) {
            return projectContext.effectiveCwd();
        }
        return null;
    }

    /** 判断路径是否为相对路径 (非 Windows 盘符/UNC/Unix 绝对路径) */
    private static boolean isRelative(String path) {
        if (path.startsWith("/") || path.startsWith("\\")) {
            return false;
        }
        // Windows 盘符: C:\ 或 C:/
        return !(path.length() >= 2 && Character.isLetter(path.charAt(0)) && path.charAt(1) == ':');
    }

    /** 以项目根为基准拼接相对路径, 分隔符跟随项目根风格 */
    private static String joinPath(String root, String relative) {
        String sep = root.contains("\\") ? "\\" : "/";
        String base = root.endsWith("/") || root.endsWith("\\")
                ? root.substring(0, root.length() - 1) : root;
        return base + sep + relative.replace(sep.equals("\\") ? "/" : "\\", sep);
    }

    /**
     * 获取当前客户端的操作系统描述，供 Agent 系统提示词使用
     */
    public String getOsDescription() {
        String platform = bridge.getClientPlatform(userId);
        if (platform == null) {
            return "未知操作系统（客户端尚未上报平台信息）";
        }
        return resolveOsHint(platform);
    }

    private String resolveOsHint(String platform) {
        if (platform == null || platform.isBlank()) {
            return null;
        }
        String p = platform.toLowerCase();
        if (p.contains(PLATFORM_WIN)) {
            return "Windows (使用 PowerShell 语法, 如 Get-ChildItem, Get-Content, Remove-Item; 也兼容 cmd 命令如 dir, type, del)";
        } else if (p.contains(PLATFORM_MAC) || p.contains(PLATFORM_DARWIN)) {
            return "macOS (请使用 Unix shell 语法)";
        } else if (p.contains(PLATFORM_LINUX)) {
            return "Linux (请使用 Unix shell 语法)";
        }
        return "平台: " + platform;
    }

    private String formatResult(String command, CommandResult result, String osHint) {
        StringBuilder sb = new StringBuilder();
        if (osHint != null) {
            sb.append("[客户端系统: ").append(osHint).append("]\n");
        }
        sb.append("命令: ").append(command).append("\n");
        sb.append("退出码: ").append(result.exitCode()).append("\n");
        sb.append("耗时: ").append(result.durationMs()).append("ms\n");

        if (result.stdout() != null && !result.stdout().isBlank()) {
            sb.append("\n--- 标准输出 ---\n");
            sb.append(result.stdout());
        }

        if (result.stderr() != null && !result.stderr().isBlank()) {
            sb.append("\n--- 标准错误 ---\n");
            sb.append(result.stderr());
        }

        if (result.stdout() != null && result.stdout().isBlank()
                && result.stderr() != null && result.stderr().isBlank()) {
            sb.append("\n(无输出)");
        }

        return sb.toString();
    }
}
