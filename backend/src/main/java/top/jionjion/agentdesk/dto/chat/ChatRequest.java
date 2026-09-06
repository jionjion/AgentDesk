package top.jionjion.agentdesk.dto.chat;

/**
 * 流式对话请求体
 */
public record ChatRequest(
        String sessionId,
        String message,
        String fileIds,
        String kbIds,
        String memoryMode,

        /* 本设备针对会话所绑项目的 runtime snapshot（可空; 未绑定项目或本机无位置时为 null）*/
        RuntimeSnapshot runtimeSnapshot
) {

    /**
     * 客户端本轮上报的项目运行时快照。
     * 仅描述设备本地状态; projectId 归属校验以服务器数据库为准。
     */
    public record RuntimeSnapshot(
            /* 项目ID, 必须与会话绑定的项目一致 */
            String projectId,
            /* 设备ID */
            String deviceId,
            /* 项目本地根目录 */
            String rootPath,
            /* 默认工作目录 (可空) */
            String cwd,
            /* 客户端平台: win32/darwin/linux */
            String platform,
            /* Python 解释器路径 (可空) */
            String pythonExecutable,
            /* Python 版本 (可空) */
            String pythonVersion
    ) {
    }
}
