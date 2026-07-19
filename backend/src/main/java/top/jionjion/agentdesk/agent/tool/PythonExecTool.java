package top.jionjion.agentdesk.agent.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.jionjion.agentdesk.agent.exec.ClientExecException;
import top.jionjion.agentdesk.agent.exec.ClientExecutor;
import top.jionjion.agentdesk.agent.exec.ExecSpec;
import top.jionjion.agentdesk.agent.runtime.ProjectRuntimeContext;
import top.jionjion.agentdesk.websocket.dto.CommandRequest;
import top.jionjion.agentdesk.websocket.dto.CommandResult;

import java.util.Arrays;
import java.util.List;

/**
 * 本地 Python 执行工具: 用项目配置的真实解释器在用户本机执行 Python 代码 (见开发计划 9.4/10.2)。
 * <p>
 * 规则:
 * <ul>
 *   <li>code 与 script_path 二选一; code 通过 stdin 传给 {@code python -}, 不拼接 {@code python -c}</li>
 *   <li>解释器由客户端 ProjectLocation 决定, 模型不能指定可执行文件</li>
 *   <li>cwd 为空时使用项目根; 相对 cwd 按项目根解析</li>
 *   <li>默认按高风险处理, 需要用户审批 (见开发计划 10.3)</li>
 * </ul>
 *
 * @author Jion
 */
public class PythonExecTool {

    private static final Logger log = LoggerFactory.getLogger(PythonExecTool.class);

    private final ClientExecutor bridge;
    private final Long userId;
    private final String sessionId;

    public PythonExecTool(ClientExecutor bridge, Long userId, String sessionId) {
        this.bridge = bridge;
        this.userId = userId;
        this.sessionId = sessionId;
    }

    @Tool(name = ToolDefinitions.PYTHON_EXEC, description = ToolDefinitions.PYTHON_EXEC_DESC)
    public String execute(
            @ToolParam(name = "code", description = "要执行的 Python 代码 (与 script_path 二选一)。代码通过 stdin 传给解释器执行", required = false) String code,
            @ToolParam(name = "script_path", description = "要执行的现有 Python 脚本路径 (与 code 二选一)。相对路径按项目根解析", required = false) String scriptPath,
            @ToolParam(name = "args", description = "传给脚本/代码的命令行参数列表, 可选", required = false) List<String> args,
            @ToolParam(name = "cwd", description = "本次执行的工作目录, 可选。相对路径按项目根解析; 不传则使用项目根目录", required = false) String cwd,
            @ToolParam(name = "timeout_ms", description = "超时毫秒, 可选。超过服务端上限时按上限执行", required = false) Integer timeoutMs,
            ProjectRuntimeContext projectContext
    ) {
        boolean hasCode = code != null && !code.isBlank();
        boolean hasScript = scriptPath != null && !scriptPath.isBlank();
        if (hasCode == hasScript) {
            return "错误: code 与 script_path 必须二选一";
        }

        if (!bridge.isConnected(userId)) {
            return "错误: 用户的桌面客户端未连接, 无法执行 Python 代码。请提示用户启动桌面客户端并确保远程执行功能已开启。";
        }

        if (projectContext == null || !projectContext.runtimeOnline()) {
            return "错误: 当前会话未绑定项目或本地运行环境未连接, 无法确定 Python 解释器。请提示用户在聊天界面选择项目并绑定本机目录。";
        }
        if (projectContext.pythonExecutable() == null || projectContext.pythonExecutable().isBlank()) {
            return "错误: 当前项目未检测到可用的 Python 解释器。请提示用户在项目设置中配置 Python, 或在项目目录创建 .venv 虚拟环境。";
        }

        String effectiveCwd = LocalPathResolver.resolveWorkingDir(cwd, projectContext);
        String resolvedScript = hasScript
                ? LocalPathResolver.resolveFilePath(scriptPath, projectContext) : null;

        log.info("本地 Python 执行: userId={}, session={}, mode={}, cwd={}",
                userId, sessionId, hasCode ? "code" : "script", effectiveCwd);

        try {
            ExecSpec spec = ExecSpec.python(
                    hasCode ? code : null,
                    resolvedScript,
                    args,
                    effectiveCwd,
                    // python_exec 默认高风险 (见开发计划 10.3)
                    CommandRequest.RISK_HIGH,
                    projectContext.projectId(),
                    projectContext.deviceId(),
                    timeoutMs != null && timeoutMs > 0 ? timeoutMs.longValue() : null);
            CommandResult result = bridge.executeExec(userId, sessionId, spec);
            return formatResult(hasCode ? "(内联代码)" : resolvedScript, args, result, projectContext);
        } catch (ClientExecException e) {
            return "Python 执行失败: " + e.getMessage();
        }
    }

    private String formatResult(String target, List<String> args, CommandResult result,
                                ProjectRuntimeContext projectContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("Python: ").append(projectContext.pythonVersion() != null
                ? projectContext.pythonVersion() : "未知版本");
        sb.append(" (").append(projectContext.pythonExecutable()).append(")\n");
        sb.append("目标: ").append(target);
        if (args != null && !args.isEmpty()) {
            sb.append(" ").append(String.join(" ", args));
        }
        sb.append("\n");
        sb.append("退出码: ").append(result.exitCode()).append("\n");
        sb.append("耗时: ").append(result.durationMs()).append("ms\n");

        if (result.stdout() != null && !result.stdout().isBlank()) {
            sb.append("\n--- 标准输出 ---\n").append(result.stdout());
        }
        if (result.stderr() != null && !result.stderr().isBlank()) {
            sb.append("\n--- 标准错误 ---\n").append(result.stderr());
        }
        if ((result.stdout() == null || result.stdout().isBlank())
                && (result.stderr() == null || result.stderr().isBlank())) {
            sb.append("\n(无输出)");
        }
        return sb.toString();
    }
}
