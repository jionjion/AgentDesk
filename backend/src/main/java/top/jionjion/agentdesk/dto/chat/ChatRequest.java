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
        /** 用户当前工作目录（前端选择的项目目录），传入后会话中的远程执行默认使用此目录 */
        String workingDir
) {

    /**
     * 沙箱上下文（前端附带，用于注入 AI system prompt）
     */
    public record SandboxContext(
            /** 工具描述文本 */
            String tools,
            /** 已同步到沙箱的文件列表 */
            List<String> files
    ) {
    }
}
