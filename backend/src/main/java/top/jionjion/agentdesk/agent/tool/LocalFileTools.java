package top.jionjion.agentdesk.agent.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.jionjion.agentdesk.agent.exec.ClientExecException;
import top.jionjion.agentdesk.agent.exec.ClientExecutor;
import top.jionjion.agentdesk.agent.runtime.ProjectRuntimeContext;
import top.jionjion.agentdesk.websocket.dto.CommandRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地文件工具集: 通过本地文件 RPC (local_fs_request/result, 见开发计划 8.3) 在用户本机
 * 读取、写入、编辑、列举与搜索文件。
 * <p>
 * 路径规则: 相对路径以项目根为基准, 绝对路径允许。
 * 风险规则 (见开发计划 10.3): 只读操作低风险; 写入/编辑操作在项目目录外时提升为高风险。
 * 输出预算: 客户端按 maxBytes/maxLines/maxEntries/maxMatches 截断并带 truncated 标记,
 * 后端把结果交给模型前再执行一次字符截断, 形成双层限制。
 *
 * @author Jion
 */
public class LocalFileTools {

    private static final Logger log = LoggerFactory.getLogger(LocalFileTools.class);

    /** 模型上下文层的结果字符上限 (见开发计划 8.3, maxOutputChars) */
    private static final int MAX_OUTPUT_CHARS = 64_000;

    private final ClientExecutor bridge;
    private final Long userId;
    private final String sessionId;

    public LocalFileTools(ClientExecutor bridge, Long userId, String sessionId) {
        this.bridge = bridge;
        this.userId = userId;
        this.sessionId = sessionId;
    }

    // ==================== 读取 ====================

    @Tool(name = ToolDefinitions.LOCAL_READ_FILE, description = ToolDefinitions.LOCAL_READ_FILE_DESC)
    public String readFile(
            @ToolParam(name = "path", description = "文件路径。相对路径按项目根解析") String path,
            @ToolParam(name = "offset", description = "起始行号 (从 1 开始), 可选。用于分块读取大文件", required = false) Integer offset,
            @ToolParam(name = "limit", description = "读取的最大行数, 可选", required = false) Integer limit,
            ProjectRuntimeContext projectContext
    ) {
        String precheck = precheck(path, projectContext);
        if (precheck != null) {
            return precheck;
        }
        Map<String, Object> request = baseRequest("read", path, projectContext);
        if (offset != null && offset > 0) {
            request.put("offset", offset);
        }
        if (limit != null && limit > 0) {
            request.put("limit", limit);
        }
        return dispatch(request, result -> {
            StringBuilder sb = new StringBuilder();
            sb.append("文件: ").append(result.get("path")).append("\n");
            Object totalLines = result.get("totalLines");
            if (totalLines != null) {
                sb.append("总行数: ").append(totalLines).append("\n");
            }
            sb.append("\n").append(stringValue(result.get("content")));
            if (Boolean.TRUE.equals(result.get("truncated"))) {
                sb.append("\n\n[内容已截断");
                Object nextOffset = result.get("nextOffset");
                if (nextOffset != null) {
                    sb.append(", 继续读取请传 offset=").append(nextOffset);
                }
                sb.append("]");
            }
            return sb.toString();
        });
    }

    // ==================== 写入 ====================

    @Tool(name = ToolDefinitions.LOCAL_WRITE_FILE, description = ToolDefinitions.LOCAL_WRITE_FILE_DESC)
    public String writeFile(
            @ToolParam(name = "path", description = "文件路径。相对路径按项目根解析; 不存在时创建, 存在时整文件覆盖") String path,
            @ToolParam(name = "content", description = "要写入的完整文本内容 (UTF-8), 上限 2MB") String content,
            ProjectRuntimeContext projectContext
    ) {
        String precheck = precheck(path, projectContext);
        if (precheck != null) {
            return precheck;
        }
        if (content == null) {
            return "错误: content 不能为空 (写入空文件请传空字符串)";
        }
        Map<String, Object> request = baseRequest("write", path, projectContext);
        request.put("content", content);
        request.put("riskLevel", classifyWriteRisk(path, projectContext));
        return dispatch(request, result -> {
            StringBuilder sb = new StringBuilder();
            sb.append("已写入: ").append(result.get("path"));
            Object bytes = result.get("bytesWritten");
            if (bytes != null) {
                sb.append(" (").append(bytes).append(" 字节)");
            }
            return sb.toString();
        });
    }

    // ==================== 编辑 ====================

    @Tool(name = ToolDefinitions.LOCAL_EDIT_FILE, description = ToolDefinitions.LOCAL_EDIT_FILE_DESC)
    public String editFile(
            @ToolParam(name = "path", description = "文件路径。相对路径按项目根解析") String path,
            @ToolParam(name = "old_text", description = "要被替换的原文本。必须与文件内容精确匹配 (含缩进)") String oldText,
            @ToolParam(name = "new_text", description = "替换后的新文本") String newText,
            @ToolParam(name = "replace_all", description = "是否替换所有出现, 默认 false (要求精确出现一次)", required = false) Boolean replaceAll,
            @ToolParam(name = "expected_replacements", description = "预期替换次数, 默认 1。实际出现次数不一致时不修改文件", required = false) Integer expectedReplacements,
            ProjectRuntimeContext projectContext
    ) {
        String precheck = precheck(path, projectContext);
        if (precheck != null) {
            return precheck;
        }
        if (oldText == null || oldText.isEmpty()) {
            return "错误: old_text 不能为空";
        }
        if (newText == null) {
            return "错误: new_text 不能为 null";
        }
        Map<String, Object> request = baseRequest("edit", path, projectContext);
        request.put("oldText", oldText);
        request.put("newText", newText);
        request.put("replaceAll", Boolean.TRUE.equals(replaceAll));
        request.put("expectedReplacements", expectedReplacements != null ? expectedReplacements : 1);
        request.put("riskLevel", classifyWriteRisk(path, projectContext));
        return dispatch(request, result ->
                "已编辑: " + result.get("path") + " (替换 " + result.get("replacements") + " 处)");
    }

    // ==================== 列举 ====================

    @Tool(name = ToolDefinitions.LOCAL_LIST_FILES, description = ToolDefinitions.LOCAL_LIST_FILES_DESC)
    public String listFiles(
            @ToolParam(name = "path", description = "目录路径, 可选。相对路径按项目根解析; 不传则使用项目根目录", required = false) String path,
            @ToolParam(name = "pattern", description = "glob 模式, 可选 (如 src/**/*.ts)。传入时递归匹配文件, 不传时列出目录直接子项", required = false) String pattern,
            ProjectRuntimeContext projectContext
    ) {
        String precheck = precheckContext(projectContext);
        if (precheck != null) {
            return precheck;
        }
        Map<String, Object> request = baseRequest(
                pattern != null && !pattern.isBlank() ? "glob" : "list",
                path != null && !path.isBlank() ? path : ".", projectContext);
        if (pattern != null && !pattern.isBlank()) {
            request.put("pattern", pattern);
        }
        return dispatch(request, result -> {
            StringBuilder sb = new StringBuilder();
            sb.append("目录: ").append(result.get("path")).append("\n");
            Object entries = result.get("entries");
            if (entries instanceof List<?> list) {
                sb.append("共 ").append(list.size()).append(" 项:\n");
                for (Object entry : list) {
                    sb.append(entry).append("\n");
                }
            }
            if (Boolean.TRUE.equals(result.get("truncated"))) {
                sb.append("[结果已截断, 请缩小范围或使用更精确的模式]");
            }
            return sb.toString();
        });
    }

    // ==================== 搜索 ====================

    @Tool(name = ToolDefinitions.LOCAL_SEARCH_FILES, description = ToolDefinitions.LOCAL_SEARCH_FILES_DESC)
    public String searchFiles(
            @ToolParam(name = "query", description = "搜索的正则表达式或文本") String query,
            @ToolParam(name = "path", description = "搜索目录, 可选。相对路径按项目根解析; 不传则搜索项目根", required = false) String path,
            @ToolParam(name = "file_pattern", description = "限定文件范围的 glob 模式, 可选 (如 *.java)", required = false) String filePattern,
            ProjectRuntimeContext projectContext
    ) {
        String precheck = precheckContext(projectContext);
        if (precheck != null) {
            return precheck;
        }
        if (query == null || query.isBlank()) {
            return "错误: query 不能为空";
        }
        Map<String, Object> request = baseRequest("grep",
                path != null && !path.isBlank() ? path : ".", projectContext);
        request.put("query", query);
        if (filePattern != null && !filePattern.isBlank()) {
            request.put("filePattern", filePattern);
        }
        return dispatch(request, result -> {
            StringBuilder sb = new StringBuilder();
            Object matches = result.get("matches");
            Object scannedFiles = result.get("scannedFiles");
            if (matches instanceof List<?> list) {
                sb.append("匹配 ").append(list.size()).append(" 处");
                if (scannedFiles != null) {
                    sb.append(" (扫描 ").append(scannedFiles).append(" 个文件)");
                }
                sb.append(":\n");
                for (Object match : list) {
                    sb.append(match).append("\n");
                }
            }
            if (Boolean.TRUE.equals(result.get("truncated"))) {
                sb.append("[匹配结果已截断, 请缩小搜索范围]");
            }
            return sb.toString();
        });
    }

    // ==================== 内部方法 ====================

    private String precheck(String path, ProjectRuntimeContext projectContext) {
        if (path == null || path.isBlank()) {
            return "错误: path 不能为空";
        }
        return precheckContext(projectContext);
    }

    private String precheckContext(ProjectRuntimeContext projectContext) {
        if (!bridge.isConnected(userId)) {
            return "错误: 用户的桌面客户端未连接, 无法访问本地文件。请提示用户启动桌面客户端。";
        }
        if (projectContext == null || !projectContext.runtimeOnline()) {
            return "错误: 当前会话未绑定项目或本地运行环境未连接, 无法访问本地文件。请提示用户选择项目并绑定本机目录。";
        }
        return null;
    }

    private Map<String, Object> baseRequest(String op, String path, ProjectRuntimeContext projectContext) {
        Map<String, Object> request = new HashMap<>();
        request.put("op", op);
        request.put("path", LocalPathResolver.resolveFilePath(path, projectContext));
        request.put("projectId", projectContext.projectId());
        request.put("deviceId", projectContext.deviceId());
        return request;
    }

    /**
     * 结构化文件工具的写入风险: 项目目录外的写入/编辑提升为高风险 (见开发计划 10.3)。
     */
    private String classifyWriteRisk(String path, ProjectRuntimeContext projectContext) {
        String resolved = LocalPathResolver.resolveFilePath(path, projectContext);
        return LocalPathResolver.isInsideProject(resolved, projectContext)
                ? CommandRequest.RISK_LOW
                : CommandRequest.RISK_HIGH;
    }

    private String dispatch(Map<String, Object> request, ResultFormatter formatter) {
        try {
            Map<String, Object> result = bridge.executeLocalFs(userId, sessionId, request);
            if (result == null) {
                return "错误: 客户端未返回结果";
            }
            if (!Boolean.TRUE.equals(result.get("success"))) {
                return "本地文件操作失败: " + stringValue(result.get("error"));
            }
            return truncateForModel(formatter.format(result));
        } catch (ClientExecException e) {
            return "本地文件操作失败: " + e.getMessage();
        } catch (Exception e) {
            log.warn("本地文件工具异常: {}", e.getMessage());
            return "本地文件操作异常: " + e.getMessage();
        }
    }

    private String truncateForModel(String text) {
        if (text.length() <= MAX_OUTPUT_CHARS) {
            return text;
        }
        return text.substring(0, MAX_OUTPUT_CHARS) + "\n[结果已截断, 共 " + text.length() + " 字符]";
    }

    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    @FunctionalInterface
    private interface ResultFormatter {
        String format(Map<String, Object> result);
    }
}
