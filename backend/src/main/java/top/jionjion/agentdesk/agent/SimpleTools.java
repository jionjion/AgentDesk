package top.jionjion.agentdesk.agent;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import top.jionjion.agentdesk.entity.FileRecord;
import top.jionjion.agentdesk.repository.FileRecordRepository;
import top.jionjion.agentdesk.service.OssService;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * 示例工具类, 提供给 Agent 调用
 *
 * @author Jion
 */
public class SimpleTools {

    private static final int BYTES_PER_KB = 1024;
    private static final int BYTES_PER_MB = 1024 * 1024;
    private static final double KB_DIVISOR = 1024.0;
    private static final String FORMAT_KB = "%.1fKB";
    private static final String FORMAT_MB = "%.1fMB";
    private static final String UNIT_BYTE = "B";
    private static final String TEXT_CONTENT_TYPE_PREFIX = "text/";
    private static final int MAX_READ_SIZE = 512 * 1024;
    private static final char CHAR_PLUS = '+';
    private static final char CHAR_MINUS = '-';
    private static final char CHAR_LPAREN = '(';
    private static final char CHAR_RPAREN = ')';
    private static final char CHAR_DOT = '.';
    private static final char CHAR_ZERO = '0';
    private static final char CHAR_NINE = '9';

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "txt", "md", "csv", "json", "xml", "log",
            "java", "py", "js", "ts", "html", "css",
            "yaml", "yml", "properties", "sql", "sh"
    );

    private final FileRecordRepository fileRecordRepository;
    private final OssService ossService;

    public SimpleTools(FileRecordRepository fileRecordRepository, OssService ossService) {
        this.fileRecordRepository = fileRecordRepository;
        this.ossService = ossService;
    }

    @Tool(name = "get_current_time", description = "获取当前时间, 可以指定时区")
    public String getCurrentTime(@ToolParam(name = "timezone", description = "时区, 例如: Asia/Shanghai, America/New_York") String timezone) {
        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(timezone);
        } catch (Exception e) {
            zoneId = ZoneId.of("Asia/Shanghai");
        }
        return LocalDateTime.now(zoneId)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    @Tool(name = "calculate", description = "计算一个数学表达式, 支持加减乘除")
    public String calculate(@ToolParam(name = "expression", description = "数学表达式, 例如: 1+2*3") String expression) {
        try {
            // 简单的四则运算支持
            double result = evalExpression(expression);
            return String.valueOf(result);
        } catch (Exception e) {
            return "计算出错: " + e.getMessage();
        }
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

        if (record.getSize() > MAX_READ_SIZE) {
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
        if (contentType != null && contentType.startsWith(TEXT_CONTENT_TYPE_PREFIX)) {
            return true;
        }
        String ext = getExtension(fileName).toLowerCase();
        return TEXT_EXTENSIONS.contains(ext);
    }

    private String getExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1) : "";
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

    private double evalExpression(String expression) {
        final String expr = expression.replaceAll("\\s+", "");
        final int[] pos = {-1};
        final int[] ch = {0};

        Runnable nextChar = () -> ch[0] = (++pos[0] < expr.length()) ? expr.charAt(pos[0]) : -1;

        // 启动解析
        nextChar.run();
        return parseExpression(expr, pos, ch, nextChar);
    }

    private double parseExpression(String expr, int[] pos, int[] ch, Runnable nextChar) {
        double x = parseTerm(expr, pos, ch, nextChar);
        for (; ; ) {
            if (ch[0] == '+') {
                nextChar.run();
                x += parseTerm(expr, pos, ch, nextChar);
            } else if (ch[0] == '-') {
                nextChar.run();
                x -= parseTerm(expr, pos, ch, nextChar);
            } else {
                return x;
            }
        }
    }

    private double parseTerm(String expr, int[] pos, int[] ch, Runnable nextChar) {
        double x = parseFactor(expr, pos, ch, nextChar);
        for (; ; ) {
            if (ch[0] == '*') {
                nextChar.run();
                x *= parseFactor(expr, pos, ch, nextChar);
            } else if (ch[0] == '/') {
                nextChar.run();
                x /= parseFactor(expr, pos, ch, nextChar);
            } else {
                return x;
            }
        }
    }

    private double parseFactor(String expr, int[] pos, int[] ch, Runnable nextChar) {
        if (ch[0] == CHAR_PLUS) {
            nextChar.run();
            return parseFactor(expr, pos, ch, nextChar);
        }
        if (ch[0] == CHAR_MINUS) {
            nextChar.run();
            return -parseFactor(expr, pos, ch, nextChar);
        }
        double x;
        if (ch[0] == CHAR_LPAREN) {
            nextChar.run();
            x = parseExpression(expr, pos, ch, nextChar);
            if (ch[0] == CHAR_RPAREN) {
                nextChar.run();
            }
        } else {
            int startPos = pos[0];
            while (ch[0] >= CHAR_ZERO && ch[0] <= CHAR_NINE || ch[0] == CHAR_DOT) {
                nextChar.run();
            }
            x = Double.parseDouble(expr.substring(startPos, pos[0]));
        }
        return x;
    }
}
