package top.jionjion.agentdesk.agent.exec;

import top.jionjion.agentdesk.websocket.dto.CommandResult;
import top.jionjion.agentdesk.websocket.dto.SandboxResult;

/**
 * 客户端执行器: 抽象 Agent 工具与底层执行通道（当前为 WebSocket）之间的依赖。
 * <p>
 * 把 {@code RemoteExecTool} / {@code SandboxExecTool} 对具体 {@code RemoteExecBridge}
 * 的依赖收敛到此接口, 使执行层与传输实现解耦, 便于未来替换执行后端（如迁移到
 * HarnessAgent + 自定义 Sandbox）而不改动工具代码。
 *
 * @author Jion
 */
public interface ClientExecutor {

    /**
     * 检查指定用户的客户端是否已连接。
     */
    boolean isConnected(Long userId);

    /**
     * 获取客户端平台信息（如 "Windows", "macOS", "Linux"）。
     *
     * @return 平台描述, 未知时返回 null
     */
    String getClientPlatform(Long userId);

    /**
     * 向客户端发送 shell 命令并阻塞等待结果。
     *
     * @param userId     用户 ID
     * @param sessionId  聊天会话 ID
     * @param command    要执行的命令
     * @param workingDir 工作目录（可为 null）
     * @param riskLevel  风险等级
     * @return 命令执行结果
     * @throws ClientExecException 执行失败时抛出
     */
    CommandResult executeCommand(Long userId, String sessionId,
                                 String command, String workingDir,
                                 String riskLevel) throws ClientExecException;

    /**
     * 向客户端发送 Python 代码, 在沙箱中执行并等待结果。
     *
     * @param userId    用户 ID
     * @param sessionId 聊天会话 ID
     * @param code      要执行的代码
     * @return 沙箱执行结果
     * @throws ClientExecException 执行失败时抛出
     */
    SandboxResult executeSandbox(Long userId, String sessionId, String code) throws ClientExecException;
}
