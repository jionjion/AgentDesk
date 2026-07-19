package top.jionjion.agentdesk.dto.chat;

import java.util.List;

/**
 * 流式对话请求体
 */
public record ChatRequest(
        String sessionId,
        String message,
        String fileIds,
        String kbIds,
        SandboxContext sandboxContext,

        /* 用户当前工作目录（前端选择的项目目录）。过渡字段: Phase 3 移除, 由 runtimeSnapshot 取代 */
        String workingDir,

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

    /**
     * 沙箱上下文（前端附带，用于注入 AI system prompt）
     */
    public record SandboxContext(
            /* 沙箱可用工具的结构化元数据（由后端组装为工具描述文本） */
            List<SandboxTool> tools,
            /* 已同步到沙箱的文件列表 */
            List<String> files
    ) {
    }

    /**
     * 沙箱工具元数据。可变字段仅 signature/description，
     * 其余 prompt 模板由后端 {@code PromptContextBuilder} 统一组装。
     */
    public record SandboxTool(
            /* 工具签名（如 list_files(dir: str = "/data") -> list[str]） */
            String signature,
            /* 工具描述 */
            String description
    ) {
    }
}
