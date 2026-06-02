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

        /* 用户当前工作目录（前端选择的项目目录），传入后会话中的远程执行默认使用此目录 */
        String workingDir
) {

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
