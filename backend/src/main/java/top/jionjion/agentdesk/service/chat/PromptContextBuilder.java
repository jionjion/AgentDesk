package top.jionjion.agentdesk.service.chat;

import org.springframework.stereotype.Component;
import top.jionjion.agentdesk.agent.runtime.AgentInput;
import top.jionjion.agentdesk.agent.runtime.ProjectRuntimeContext;
import top.jionjion.agentdesk.dto.file.FileResponse;
import top.jionjion.agentdesk.dto.memory.MemoryItemDto;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 对话 prompt 与消息组装: 文件元信息拼装、项目上下文增强、多模态 Msg 构建。
 * <p>
 * 集中存放原 ChatController 中与消息内容构造相关的纯逻辑, 不涉及 IO 与会话状态。
 *
 * @author Jion
 */
@Component
public class PromptContextBuilder {

    private static final int BYTES_PER_KB = 1024;
    private static final int BYTES_PER_MB = 1024 * 1024;
    private static final double KB_DIVISOR = 1024.0;
    private static final String FORMAT_KB = "%.1fKB";
    private static final String FORMAT_MB = "%.1fMB";
    private static final String UNIT_BYTE = "B";
    private static final Set<String> IMAGE_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );

    /**
     * 解析逗号分隔的 fileIds 字符串
     */
    public List<Long> parseFileIds(String fileIds) {
        return parseCsvIds(fileIds);
    }

    /**
     * 解析逗号分隔的 kbIds 字符串
     */
    public List<Long> parseKbIds(String kbIds) {
        return parseCsvIds(kbIds);
    }

    private List<Long> parseCsvIds(String csv) {
        if (csv == null || csv.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .toList();
    }

    /**
     * 判断是否为图片文件
     */
    public boolean isImageFile(String contentType) {
        return contentType != null && IMAGE_CONTENT_TYPES.contains(contentType.toLowerCase());
    }

    /**
     * 根据是否含图片, 构建用户消息 (纯文本或多模态)
     */
    public AgentInput buildAgentInput(String message, List<FileResponse> imageFiles,
                                      List<FileResponse> nonImageFiles) {
        String enrichedMessage = buildMessageWithFiles(message, nonImageFiles);
        List<String> imageUrls = imageFiles.stream()
                .map(FileResponse::downloadUrl)
                .toList();
        return new AgentInput(enrichedMessage, imageUrls);
    }

    /**
     * 将当前项目上下文注入用户消息 (见开发计划 9.3)。
     * <p>
     * 数据来自服务器已验证的 Session->Project 关系与在线客户端 snapshot;
     * 原始用户消息仍按原文持久化, 本增强只影响送入模型的内容。
     * projectContext 为 null (会话未绑定项目) 时原样返回。
     */
    public String buildProjectAugmentedMessage(String message, ProjectRuntimeContext projectContext) {
        if (projectContext == null) {
            return message;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<project_context>\n");
        sb.append("项目：").append(projectContext.projectName()).append("\n");
        sb.append("项目 ID：").append(projectContext.projectId()).append("\n");
        if (projectContext.runtimeOnline()) {
            sb.append("本地根目录：").append(projectContext.rootPath()).append("\n");
            sb.append("默认工作目录：").append(projectContext.effectiveCwd()).append("\n");
            sb.append("系统：").append(describePlatform(projectContext.platform())).append("\n");
            if (projectContext.pythonExecutable() != null && !projectContext.pythonExecutable().isBlank()) {
                sb.append("Python：")
                        .append(projectContext.pythonVersion() != null ? projectContext.pythonVersion() : "未知版本")
                        .append(" (").append(projectContext.pythonExecutable()).append(")\n");
            } else {
                sb.append("Python：未检测到\n");
            }
            sb.append("本地运行环境：已连接\n");
        } else {
            sb.append("本地运行环境：未连接（本地路径与解释器信息不可用，本地执行类工具将失败）\n");
        }
        if (projectContext.instructions() != null && !projectContext.instructions().isBlank()) {
            sb.append("项目指令：").append(projectContext.instructions()).append("\n");
        }
        sb.append("</project_context>\n\n");
        sb.append(message);
        return sb.toString();
    }

    private String describePlatform(String platform) {
        if (platform == null || platform.isBlank()) {
            return "未知";
        }
        String p = platform.toLowerCase();
        if (p.contains("win")) {
            return "Windows";
        }
        if (p.contains("darwin") || p.contains("mac")) {
            return "macOS";
        }
        if (p.contains("linux")) {
            return "Linux";
        }
        return platform;
    }

    /** Adds relevant cross-session facts retrieved from Mem0 to the current turn. */
    public String buildMemoryAugmentedMessage(String message, List<MemoryItemDto> memories) {
        if (memories == null || memories.isEmpty()) {
            return message;
        }
        String facts = memories.stream()
                .map(MemoryItemDto::memory)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .map(value -> "- " + value)
                .collect(Collectors.joining("\n"));
        if (facts.isBlank()) {
            return message;
        }
        return "[与当前问题相关的长期记忆]\n"
                + "以下内容仅作为背景事实；若与用户当前输入冲突，以当前输入为准。\n"
                + facts + "\n\n---\n\n" + message;
    }

    private String buildMessageWithFiles(String message, List<FileResponse> files) {
        if (files.isEmpty()) {
            return message;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[用户上传了以下文件]\n");
        for (FileResponse f : files) {
            sb.append(String.format("- %s (大小: %s, 类型: %s, fileId: %d)\n",
                    f.originalName(),
                    formatSize(f.size()),
                    f.contentType(),
                    f.id()));
        }
        sb.append("\n[用户消息]\n");
        sb.append(message);
        return sb.toString();
    }

    private String formatSize(long bytes) {
        if (bytes < BYTES_PER_KB) {
            return bytes + UNIT_BYTE;
        }
        if (bytes < BYTES_PER_MB) {
            return String.format(FORMAT_KB, bytes / KB_DIVISOR);
        }
        return String.format(FORMAT_MB, bytes / (KB_DIVISOR * BYTES_PER_KB));
    }
}
