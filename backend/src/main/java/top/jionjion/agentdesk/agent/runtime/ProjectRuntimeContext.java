package top.jionjion.agentdesk.agent.runtime;

/**
 * 调用级项目运行时上下文 (不可变)。
 *
 * <p>每次 Agent 调用构造一次, 由服务器已验证的 Session->Project 关系与
 * 在线客户端本轮提供的 runtime snapshot 合并而成 (见开发计划 5.3)。
 *
 * <p>注意:
 * <ul>
 *   <li>Project 归属以服务器数据库为准, 不信任请求体中的 projectId</li>
 *   <li>项目根不是授权边界, 工具允许显式绝对路径</li>
 *   <li>未传 cwd 时使用 rootPath; 相对 cwd 基于 rootPath 解析</li>
 * </ul>
 *
 * @param projectId        项目ID (服务器实体)
 * @param projectName      项目名称
 * @param instructions     项目级指令 (可空)
 * @param deviceId         提供 snapshot 的设备ID (可空)
 * @param rootPath         项目在设备上的本地根目录 (可空, 离线时为 null)
 * @param cwd              默认工作目录 (可空, 为空时使用 rootPath)
 * @param platform         客户端平台 (win32/darwin/linux, 可空)
 * @param pythonExecutable Python 解释器路径 (可空)
 * @param pythonVersion    Python 版本 (可空)
 * @param runtimeOnline    本地运行环境是否在线可用
 * @author Jion
 */
public record ProjectRuntimeContext(
        String projectId,
        String projectName,
        String instructions,
        String deviceId,
        String rootPath,
        String cwd,
        String platform,
        String pythonExecutable,
        String pythonVersion,
        boolean runtimeOnline
) {

    /**
     * 有效默认工作目录: cwd 为空时回退 rootPath
     */
    public String effectiveCwd() {
        if (cwd != null && !cwd.isBlank()) {
            return cwd;
        }
        return rootPath;
    }
}
