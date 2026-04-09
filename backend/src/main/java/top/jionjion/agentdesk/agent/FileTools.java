package top.jionjion.agentdesk.agent;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import top.jionjion.agentdesk.entity.FileRecord;
import top.jionjion.agentdesk.repository.FileRecordRepository;
import top.jionjion.agentdesk.service.OssService;

import java.util.Set;

/**
 * 文件读取工具, 供子代理使用
 */
public class FileTools {

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "txt", "md", "csv", "json", "xml", "log",
            "java", "py", "js", "ts", "html", "css",
            "yaml", "yml", "properties", "sql", "sh"
    );

    private final FileRecordRepository fileRecordRepository;
    private final OssService ossService;

    public FileTools(FileRecordRepository fileRecordRepository, OssService ossService) {
        this.fileRecordRepository = fileRecordRepository;
        this.ossService = ossService;
    }

    @Tool(name = "read_file",
            description = "读取用户上传的文件内容。传入 fileId 获取文件的文本内容。" +
                    "支持的文件类型: txt, md, csv, json, xml, log, java, py, js, ts, html, css, yaml, yml, properties, sql。" +
                    "文件大小上限 500KB。")
    public String readFile(
            @ToolParam(name = "fileId", description = "文件ID，从用户上传的文件信息中获取")
            Long fileId
    ) {
        FileRecord record = fileRecordRepository.findById(fileId).orElse(null);
        if (record == null) {
            return "文件不存在: " + fileId;
        }

        if (!isTextFile(record.getContentType(), record.getOriginalName())) {
            return "不支持读取此文件类型: " + record.getContentType() +
                    "。仅支持文本类文件 (txt, md, csv, json, xml 等)";
        }

        if (record.getSize() > 512 * 1024) {
            return "文件过大 (" + formatSize(record.getSize()) +
                    ")，超过 500KB 限制。请告知用户裁剪文件后重试。";
        }

        String content = ossService.readAsText(record.getOssKey());
        return String.format("文件名: %s\n类型: %s\n大小: %s\n---\n%s",
                record.getOriginalName(),
                record.getContentType(),
                formatSize(record.getSize()),
                content);
    }

    private boolean isTextFile(String contentType, String fileName) {
        if (contentType != null && contentType.startsWith("text/")) return true;
        String ext = getExtension(fileName).toLowerCase();
        return TEXT_EXTENSIONS.contains(ext);
    }

    private String getExtension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1) : "";
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        return String.format("%.1fMB", bytes / (1024.0 * 1024));
    }
}
