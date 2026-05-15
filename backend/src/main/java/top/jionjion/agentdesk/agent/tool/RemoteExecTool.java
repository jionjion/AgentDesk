package top.jionjion.agentdesk.agent.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.jionjion.agentdesk.websocket.RemoteExecBridge;
import top.jionjion.agentdesk.websocket.RemoteExecBridge.RemoteExecException;
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

    private final RemoteExecBridge bridge;
    private final CommandRiskClassifier riskClassifier;
    private final Long userId;
    private final String sessionId;

    public RemoteExecTool(RemoteExecBridge bridge, CommandRiskClassifier riskClassifier,
                          Long userId, String sessionId) {
        this.bridge = bridge;
        this.riskClassifier = riskClassifier;
        this.userId = userId;
        this.sessionId = sessionId;
    }

    @Tool(name = ToolDefinitions.REMOTE_EXEC, description = ToolDefinitions.REMOTE_EXEC_DESC)
    public String execute(
            @ToolParam(name = "command", description = "要在用户本地机器上执行的 shell 命令。注意: 必须使用与用户操作系统匹配的命令语法") String command,
            @ToolParam(name = "working_dir", description = "命令的工作目录路径, 可选。不传则使用客户端默认目录", required = false) String workingDir
    ) {
        if (command == null || command.isBlank()) {
            return "错误: 命令不能为空";
        }

        // 检查客户端连接
        if (!bridge.isConnected(userId)) {
            return "错误: 用户的桌面客户端未连接, 无法执行远程命令。请提示用户启动桌面客户端并确保远程执行功能已开启。";
        }

        // 获取客户端平台信息
        String platform = bridge.getClientPlatform(userId);
        String osHint = resolveOsHint(platform);

        // 风险分级
        String riskLevel = riskClassifier.classify(command);
        log.info("远程执行命令: [{}] {} (userId={}, session={}, platform={})", riskLevel, command, userId, sessionId, platform);

        try {
            CommandResult result = bridge.executeCommand(userId, sessionId, command, workingDir, riskLevel);
            return formatResult(command, result, osHint);
        } catch (RemoteExecException e) {
            return "远程执行失败: " + e.getMessage();
        }
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
        if (p.contains("win")) {
            return "Windows (使用 PowerShell 语法, 如 Get-ChildItem, Get-Content, Remove-Item; 也兼容 cmd 命令如 dir, type, del)";
        } else if (p.contains("mac") || p.contains("darwin")) {
            return "macOS (请使用 Unix shell 语法)";
        } else if (p.contains("linux")) {
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
