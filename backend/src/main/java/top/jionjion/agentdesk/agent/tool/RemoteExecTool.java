package top.jionjion.agentdesk.agent.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.jionjion.agentdesk.agent.exec.ClientExecException;
import top.jionjion.agentdesk.agent.exec.ClientExecutor;
import top.jionjion.agentdesk.websocket.dto.CommandResult;

/**
 * 远程命令执行工具: Agent 通过此工具在用户的本地机器上执行 shell 命令。
 * <p>
 * 命令通过 WebSocket 发送到客户端执行, 根据风险等级决定是否需要用户确认。
 * 低风险命令（如 ls, cat, git status）自动执行, 高风险命令需要用户在客户端确认。
 *
 * @author Jion
 */
public class RemoteExecTool {

    private static final Logger log = LoggerFactory.getLogger(RemoteExecTool.class);

    private static final String CHAIN_AND = "&&";
    private static final String CHAIN_SEMICOLON = ";";
    private static final String PIPE = "|";
    private static final String CMD_CD = "cd ";
    private static final String CMD_SET_LOCATION = "Set-Location ";
    private static final String QUOTE_DOUBLE = "\"";
    private static final String QUOTE_SINGLE = "'";
    private static final String PLATFORM_WIN = "win";
    private static final String PLATFORM_DARWIN = "darwin";
    private static final String PLATFORM_MAC = "mac";
    private static final String PLATFORM_LINUX = "linux";

    private final ClientExecutor bridge;
    private final CommandRiskClassifier riskClassifier;
    private final Long userId;
    private final String sessionId;

    /**
     * 会话级当前工作目录: 记住上一次 cd 或 working_dir 的路径, 后续命令默认使用
     */
    private volatile String currentWorkingDir;

    public RemoteExecTool(ClientExecutor bridge, CommandRiskClassifier riskClassifier,
                          Long userId, String sessionId) {
        this.bridge = bridge;
        this.riskClassifier = riskClassifier;
        this.userId = userId;
        this.sessionId = sessionId;
    }

    /**
     * 设置当前工作目录（供外部在每次请求时注入前端选择的工作目录）
     */
    public void setWorkingDir(String workingDir) {
        if (workingDir != null && !workingDir.isBlank()) {
            this.currentWorkingDir = workingDir;
        }
    }

    @Tool(name = ToolDefinitions.REMOTE_EXEC, description = ToolDefinitions.REMOTE_EXEC_DESC)
    public String execute(
            @ToolParam(name = "command", description = "要在用户本地机器上执行的 shell 命令。注意: 必须使用与用户操作系统匹配的命令语法") String command,
            @ToolParam(name = "working_dir", description = "命令的工作目录路径, 可选。不传则使用会话中上一次的工作目录", required = false) String workingDir
    ) {
        if (command == null || command.isBlank()) {
            return "错误: 命令不能为空";
        }

        // 检查客户端连接
        if (!bridge.isConnected(userId)) {
            return "错误: 用户的桌面客户端未连接, 无法执行远程命令。请提示用户启动桌面客户端并确保远程执行功能已开启。";
        }

        // 解析工作目录: 显式传入 > 会话记忆 > 客户端默认
        String effectiveDir = resolveWorkingDir(workingDir);

        // 处理 cd 命令: 更新会话级工作目录
        String cdTarget = extractCdTarget(command);
        if (cdTarget != null) {
            currentWorkingDir = cdTarget;
            log.info("会话工作目录切换为: {} (userId={}, session={})", cdTarget, userId, sessionId);
            return "工作目录已切换到: " + cdTarget;
        }

        // 如果显式传入了 working_dir, 同时更新会话记忆
        if (workingDir != null && !workingDir.isBlank()) {
            currentWorkingDir = workingDir;
        }

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
     * 解析有效工作目录: 显式传入优先, 其次会话记忆, 最后为 null（客户端使用默认）
     */
    private String resolveWorkingDir(String workingDir) {
        if (workingDir != null && !workingDir.isBlank()) {
            return workingDir;
        }
        return currentWorkingDir;
    }

    /**
     * 提取 cd 命令的目标路径。仅处理纯 cd 命令（不含 && 或 ;），返回 null 表示非 cd 命令。
     */
    private String extractCdTarget(String command) {
        String trimmed = command.trim();
        // 仅匹配纯 cd 命令, 不处理组合命令（如 cd /path && ls）
        if (trimmed.contains(CHAIN_AND) || trimmed.contains(CHAIN_SEMICOLON) || trimmed.contains(PIPE)) {
            return null;
        }
        if (trimmed.startsWith(CMD_CD) || trimmed.startsWith(CMD_SET_LOCATION)) {
            String target = trimmed.startsWith(CMD_CD)
                    ? trimmed.substring(CMD_CD.length()).trim()
                    : trimmed.substring(CMD_SET_LOCATION.length()).trim();
            // 去掉引号
            if (isQuoted(target)) {
                target = target.substring(1, target.length() - 1);
            }
            return target.isEmpty() ? null : target;
        }
        return null;
    }

    /**
     * 判断字符串是否被成对的双引号或单引号包裹
     *
     * @param value 待判断的字符串
     * @return 被成对引号包裹返回 true
     */
    private static boolean isQuoted(String value) {
        boolean doubleQuoted = value.startsWith(QUOTE_DOUBLE) && value.endsWith(QUOTE_DOUBLE);
        boolean singleQuoted = value.startsWith(QUOTE_SINGLE) && value.endsWith(QUOTE_SINGLE);
        return doubleQuoted || singleQuoted;
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
