package top.jionjion.agentdesk.service.chat;

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.URLSource;
import org.springframework.stereotype.Component;
import top.jionjion.agentdesk.dto.chat.ChatRequest;
import top.jionjion.agentdesk.dto.file.FileResponse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 对话 prompt 与消息组装: 文件元信息拼装、沙箱上下文增强、多模态 Msg 构建。
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
    public Msg buildUserMsg(String message, List<FileResponse> imageFiles, List<FileResponse> nonImageFiles) {
        if (imageFiles.isEmpty()) {
            String enrichedMessage = buildMessageWithFiles(message, nonImageFiles);
            return Msg.builder().textContent(enrichedMessage).build();
        }
        return buildMultimodalMsg(message, imageFiles, nonImageFiles);
    }

    /**
     * 将沙箱上下文（工具描述 + 文件列表）拼入用户消息
     */
    public String buildSandboxAugmentedMessage(String message, ChatRequest.SandboxContext sandboxContext) {
        if (sandboxContext == null) {
            return message;
        }

        StringBuilder sb = new StringBuilder();

        // 注入文件列表
        if (sandboxContext.files() != null && !sandboxContext.files().isEmpty()) {
            sb.append("[本地 Python 沙箱文件]\n");
            sb.append("以下文件已加载到用户本地的 Python 沙箱 /data/ 目录中，可通过 sandbox_exec 工具执行代码访问：\n");
            for (String file : sandboxContext.files()) {
                sb.append("- /data/").append(file).append("\n");
            }
            sb.append("\n");
        }

        // 注入工具描述
        String toolDescriptions = buildToolDescriptions(sandboxContext.tools());
        if (!toolDescriptions.isBlank()) {
            sb.append("[沙箱中可用的 Python 工具函数]\n");
            sb.append(toolDescriptions).append("\n\n");
        }

        // 使用指南
        if (!sb.isEmpty()) {
            sb.append("[使用方式]\n");
            sb.append("当需要处理上述文件或进行数据分析时，使用 sandbox_exec 工具执行 Python 代码。\n");
            sb.append("代码中可直接使用 tools.* 函数和 /data/ 下的文件。\n");
            sb.append("将需要展示的结果赋值给 result 变量。\n");
            sb.append("示例：sandbox_exec(code=\"pages = tools.read_pdf('/data/xx.pdf')\\nresult = pages[0][:200]\")\n\n");
            sb.append("---\n\n");
        }

        sb.append(message);
        return sb.toString();
    }

    /**
     * 由结构化工具元数据重建工具描述 Markdown。
     * <p>
     * 输出与前端原 {@code getToolDescriptions()} 逐字符等价: 头部固定模板 + 逐项
     * {@code - `tools.{signature}` — {description}} + 固定使用示例。无启用工具时返回空串。
     */
    public String buildToolDescriptions(List<ChatRequest.SandboxTool> tools) {
        if (tools == null || tools.isEmpty()) {
            return "";
        }
        String items = tools.stream()
                .map(t -> "- `tools." + t.signature() + "` — " + t.description())
                .collect(Collectors.joining("\n"));
        return "## 沙箱可用工具函数\n"
                + "\n"
                + "以下工具函数已预装在沙箱环境中，可直接通过 `tools.` 命名空间调用：\n"
                + "\n"
                + items + "\n"
                + "\n"
                + "使用示例：\n"
                + "```python\n"
                + "files = tools.list_files()\n"
                + "df = tools.to_dataframe('/data/sales.xlsx')\n"
                + "tools.save_file(df, 'result.csv')\n"
                + "```";
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

    /**
     * 构建多模态消息 (含图片 ImageBlock)
     */
    private Msg buildMultimodalMsg(String message, List<FileResponse> imageFiles, List<FileResponse> nonImageFiles) {
        List<ContentBlock> blocks = new ArrayList<>();

        // 文本块: 用户消息 + 非图片文件描述
        String textPart = buildMessageWithFiles(message, nonImageFiles);
        blocks.add(TextBlock.builder().text(textPart).build());

        // 图片块: 使用 OSS 预签名 URL
        for (FileResponse img : imageFiles) {
            blocks.add(ImageBlock.builder()
                    .source(URLSource.builder()
                            .url(img.downloadUrl())
                            .build())
                    .build());
        }

        return Msg.builder()
                .role(MsgRole.USER)
                .content(blocks)
                .build();
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
