package top.jionjion.agentdesk.agent.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.jionjion.agentdesk.agent.exec.ClientExecException;
import top.jionjion.agentdesk.agent.exec.ClientExecutor;
import top.jionjion.agentdesk.websocket.dto.SandboxResult;

/**
 * 沙箱代码执行工具: Agent 通过此工具在用户浏览器端的 Pyodide 沙箱中执行 Python 代码。
 * <p>
 * 代码通过 WebSocket 发送到前端客户端，在 Pyodide (WASM) 沙箱中执行，
 * 执行结果（stdout、stderr、返回值、图表）通过 WebSocket 回传。
 * <p>
 * 沙箱中预装了 tools.* 命名空间的工具函数（如 tools.read_pdf、tools.to_dataframe 等），
 * 工作目录文件挂载在 /data/ 下。
 *
 * @author Jion
 */
public class SandboxExecTool {

    private static final Logger log = LoggerFactory.getLogger(SandboxExecTool.class);

    private final ClientExecutor bridge;
    private final Long userId;
    private final String sessionId;

    public SandboxExecTool(ClientExecutor bridge, Long userId, String sessionId) {
        this.bridge = bridge;
        this.userId = userId;
        this.sessionId = sessionId;
    }

    @Tool(name = ToolDefinitions.SANDBOX_EXEC, description = ToolDefinitions.SANDBOX_EXEC_DESC)
    public String execute(
            @ToolParam(name = "code", description = "要在 Python 沙箱中执行的代码。可使用 tools.* 工具函数、pandas、numpy、matplotlib 等。将结果赋值给 result 变量以便返回。") String code
    ) {
        if (code == null || code.isBlank()) {
            return "错误: 代码不能为空";
        }

        // 检查客户端连接
        if (!bridge.isConnected(userId)) {
            return "错误: 用户的桌面客户端未连接, 无法执行沙箱代码。";
        }

        log.info("沙箱执行代码: userId={}, session={}, code长度={}", userId, sessionId, code.length());

        try {
            SandboxResult result = bridge.executeSandbox(userId, sessionId, code);
            return formatResult(result);
        } catch (ClientExecException e) {
            return "沙箱执行失败: " + e.getMessage();
        }
    }

    private String formatResult(SandboxResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("执行").append(result.success() ? "成功" : "失败");
        sb.append(" (耗时: ").append(result.durationMs()).append("ms)\n");

        if (result.stdout() != null && !result.stdout().isBlank()) {
            sb.append("\n--- 标准输出 ---\n");
            sb.append(result.stdout());
        }

        if (result.stderr() != null && !result.stderr().isBlank()) {
            sb.append("\n--- 错误信息 ---\n");
            sb.append(result.stderr());
        }

        if (result.result() != null && !result.result().isBlank()) {
            sb.append("\n--- 返回值 (result 变量) ---\n");
            sb.append(result.result());
        }

        if (result.figureCount() > 0) {
            sb.append("\n[生成了 ").append(result.figureCount()).append(" 个图表，已在前端展示]");
        }

        return sb.toString();
    }
}
