package top.jionjion.agentdesk.agent.exec;

import top.jionjion.agentdesk.websocket.dto.CommandResult;

import java.util.Map;

/**
 * 客户端执行器: 抽象 Agent 工具与底层执行通道（当前为 WebSocket）之间的依赖。
 * <p>
 * 把本地执行工具对具体 {@code RemoteExecBridge} 的依赖收敛到此接口,
 * 使执行层与传输实现解耦, 便于未来替换执行后端而不改动工具代码。
 *
 * @author Jion
 */
public interface ClientExecutor {

    /**
     * 检查指定用户的客户端是否已连接。
     *
     * @param userId 用户 ID
     * @return 已连接返回 true, 否则返回 false
     */
    boolean isConnected(Long userId);

    /**
     * 获取客户端平台信息（如 "Windows", "macOS", "Linux"）。
     *
     * @param userId 用户 ID
     * @return 平台描述, 未知时返回 null
     */
    String getClientPlatform(Long userId);

    /**
     * 向客户端发送统一本地执行请求 (shell / python) 并阻塞等待结果。
     *
     * @param userId    用户 ID
     * @param sessionId 聊天会话 ID
     * @param spec      执行规格 (见开发计划 8.2)
     * @return 执行结果
     * @throws ClientExecException 执行失败时抛出
     */
    CommandResult executeExec(Long userId, String sessionId, ExecSpec spec) throws ClientExecException;

    /**
     * 向客户端发送本地文件 RPC 请求 (read/write/edit/list/glob/grep/stat) 并阻塞等待结果。
     *
     * @param userId    用户 ID
     * @param sessionId 聊天会话 ID
     * @param request   请求载荷 (op/path/... 见开发计划 8.3)
     * @return 结果载荷
     * @throws ClientExecException 执行失败时抛出
     */
    Map<String, Object> executeLocalFs(Long userId, String sessionId, Map<String, Object> request)
            throws ClientExecException;
}
