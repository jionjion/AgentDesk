package top.jionjion.agentdesk.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import top.jionjion.agentdesk.dto.settings.ObsidianSettingsDto;
import top.jionjion.agentdesk.entity.ChatMessage;
import top.jionjion.agentdesk.entity.SessionMetadata;
import top.jionjion.agentdesk.repository.ChatMessageRepository;
import top.jionjion.agentdesk.repository.SessionRepository;
import top.jionjion.agentdesk.security.UserContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Obsidian 知识沉淀服务
 * <p>
 * 负责将对话内容导出为 Obsidian 兼容的 Markdown 文件，写入用户配置的 Vault 目录。
 *
 * @author Jion
 */
@Service
public class ObsidianService {

    private static final Logger log = LoggerFactory.getLogger(ObsidianService.class);
    private static final int MAX_NAME_LENGTH = 80;
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            .withZone(ZoneId.systemDefault());

    private final SettingsService settingsService;
    private final ChatMessageRepository chatMessageRepository;
    private final SessionRepository sessionRepository;
    private final ExportValueService exportValueService;

    public ObsidianService(SettingsService settingsService,
                           ChatMessageRepository chatMessageRepository,
                           SessionRepository sessionRepository,
                           ExportValueService exportValueService) {
        this.settingsService = settingsService;
        this.chatMessageRepository = chatMessageRepository;
        this.sessionRepository = sessionRepository;
        this.exportValueService = exportValueService;
    }

    /**
     * 导出单条 AI 回复到 Obsidian
     *
     * @param messageId 消息ID
     * @param category  分类目录名（为 null 时使用默认分类）
     */
    public void exportMessage(Long messageId, String category) {
        Long userId = UserContext.getUserId();
        ObsidianSettingsDto settings = settingsService.getObsidianSettings(userId);
        validateVaultConfigured(settings);

        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "消息不存在"));

        // 验证消息所属会话归当前用户所有
        sessionRepository.findByIdAndUserId(message.getSessionId(), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问此消息"));

        // 查找该消息前面最近的一条 user 消息作为"问题"
        List<ChatMessage> sessionMessages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(message.getSessionId());
        String question = findPrecedingUserMessage(sessionMessages, message);

        // 获取会话标题
        String sessionTitle = sessionRepository.findById(message.getSessionId())
                .map(SessionMetadata::getTitle)
                .orElse("未命名对话");

        String effectiveCategory = (category != null && !category.isBlank()) ? category : settings.defaultCategory();
        String markdown = buildSingleMessageMarkdown(sessionTitle, question, message);
        String fileName = sanitizeFileName(sessionTitle) + "_" + FILE_TIME_FORMATTER.format(Instant.ofEpochMilli(message.getCreatedAt())) + ".md";

        writeToVault(settings.vaultPath(), effectiveCategory, fileName, markdown);
    }

    /**
     * 导出整个会话到 Obsidian
     *
     * @param sessionId    会话ID
     * @param autoTriggered 是否为自动触发（自动触发时会进行 AI 价值判断）
     * @return 导出结果描述
     */
    public String exportSession(String sessionId, boolean autoTriggered) {
        Long userId = UserContext.getUserId();
        ObsidianSettingsDto settings = settingsService.getObsidianSettings(userId);
        validateVaultConfigured(settings);

        SessionMetadata session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在"));

        List<ChatMessage> messages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        if (messages.isEmpty()) {
            return "会话无消息，跳过导出";
        }

        // 自动触发时进行 AI 价值判断
        if (autoTriggered) {
            String summary = buildConversationSummary(messages);
            boolean worthy = exportValueService.isWorthExporting(summary);
            if (!worthy) {
                log.info("AI 判断对话价值不足，跳过自动沉淀: sessionId={}", sessionId);
                return "对话价值不足，已跳过";
            }
        }

        String markdown = buildSessionMarkdown(session, messages);
        String fileName = sanitizeFileName(session.getTitle()) + "__" + sessionId + ".md";

        writeToVault(settings.vaultPath(), settings.defaultCategory(), fileName, markdown);
        return "会话已沉淀到 Obsidian";
    }

    /**
     * 验证路径是否存在且可写
     */
    public Map<String, Object> validatePath(String path) {
        if (path == null || path.isBlank()) {
            return Map.of("valid", false, "message", "路径不能为空");
        }
        Path vaultPath = Path.of(path);
        if (!Files.exists(vaultPath)) {
            return Map.of("valid", false, "message", "目录不存在");
        }
        if (!Files.isDirectory(vaultPath)) {
            return Map.of("valid", false, "message", "路径不是一个目录");
        }
        if (!Files.isWritable(vaultPath)) {
            return Map.of("valid", false, "message", "目录没有写入权限");
        }
        return Map.of("valid", true, "message", "验证通过");
    }

    /**
     * 列出 Vault 中已有的子目录（作为分类建议）
     */
    public List<String> listCategories() {
        Long userId = UserContext.getUserId();
        ObsidianSettingsDto settings = settingsService.getObsidianSettings(userId);
        if (settings.vaultPath() == null || settings.vaultPath().isBlank()) {
            return Collections.emptyList();
        }

        Path vaultPath = Path.of(settings.vaultPath());
        if (!Files.isDirectory(vaultPath)) {
            return Collections.emptyList();
        }

        try (Stream<Path> stream = Files.list(vaultPath)) {
            return stream
                    .filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .filter(name -> !name.startsWith("."))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            log.warn("读取 Vault 目录失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 构建对话摘要供 AI 价值判断使用（取前 5 轮对话，限制长度）
     */
    private String buildConversationSummary(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        int rounds = 0;
        for (ChatMessage msg : messages) {
            if (msg.getContent() == null || msg.getContent().isBlank()) {
                continue;
            }
            if ("user".equals(msg.getRole())) {
                sb.append("用户: ").append(truncate(msg.getContent(), 200)).append("\n");
                rounds++;
            } else if ("assistant".equals(msg.getRole())) {
                sb.append("助手: ").append(truncate(msg.getContent(), 300)).append("\n");
            }
            if (rounds >= 5) {
                break;
            }
        }
        return sb.toString();
    }

    private String truncate(String text, int maxLen) {
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "...";
    }

    private void validateVaultConfigured(ObsidianSettingsDto settings) {
        if (settings.vaultPath() == null || settings.vaultPath().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先在设置中配置 Obsidian Vault 路径");
        }
    }

    private String findPrecedingUserMessage(List<ChatMessage> messages, ChatMessage target) {
        String question = "";
        for (ChatMessage msg : messages) {
            if (msg.getId().equals(target.getId())) {
                break;
            }
            if ("user".equals(msg.getRole()) && msg.getContent() != null) {
                question = msg.getContent();
            }
        }
        return question;
    }

    private String buildSingleMessageMarkdown(String sessionTitle, String question, ChatMessage message) {
        StringBuilder sb = new StringBuilder();
        // YAML frontmatter
        sb.append("---\n");
        sb.append("source: AgentDesk\n");
        sb.append("session_id: ").append(message.getSessionId()).append("\n");
        sb.append("created: ").append(ISO_FORMATTER.format(Instant.ofEpochMilli(message.getCreatedAt()))).append("\n");
        sb.append("tags: [agentdesk]\n");
        sb.append("---\n\n");

        sb.append("# ").append(sessionTitle).append("\n\n");

        if (!question.isBlank()) {
            sb.append("## 问题\n\n");
            sb.append(question).append("\n\n");
        }

        sb.append("## 回答\n\n");
        sb.append(message.getContent() != null ? message.getContent() : "").append("\n");

        return sb.toString();
    }

    private String buildSessionMarkdown(SessionMetadata session, List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        // YAML frontmatter
        sb.append("---\n");
        sb.append("source: AgentDesk\n");
        sb.append("session_id: ").append(session.getId()).append("\n");
        sb.append("created: ").append(ISO_FORMATTER.format(Instant.ofEpochMilli(session.getCreatedAt()))).append("\n");
        sb.append("tags: [agentdesk]\n");
        sb.append("---\n\n");

        sb.append("# ").append(session.getTitle() != null ? session.getTitle() : "未命名对话").append("\n\n");

        for (ChatMessage msg : messages) {
            if ("user".equals(msg.getRole()) && msg.getContent() != null) {
                sb.append("## 问题\n\n");
                sb.append(msg.getContent()).append("\n\n");
            } else if ("assistant".equals(msg.getRole()) && msg.getContent() != null) {
                sb.append("## 回答\n\n");
                sb.append(msg.getContent()).append("\n\n");
            }
        }

        return sb.toString();
    }

    private void writeToVault(String vaultPath, String category, String fileName, String markdown) {
        try {
            String safeCategory = sanitizeCategory(category);
            Path vaultRoot = Path.of(vaultPath).normalize();
            Path targetDir = vaultRoot.resolve(safeCategory).normalize();

            // 防止路径穿越：确保目标目录仍在 Vault 根目录下
            if (!targetDir.startsWith(vaultRoot)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法的分类目录名");
            }

            Files.createDirectories(targetDir);
            Path targetFile = targetDir.resolve(fileName);
            Files.writeString(targetFile, markdown, StandardCharsets.UTF_8);
            log.info("已导出笔记到 Obsidian: {}", targetFile);
        } catch (IOException e) {
            log.error("写入 Obsidian Vault 失败: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "写入 Obsidian Vault 失败: " + e.getMessage());
        }
    }

    private String sanitizeFileName(String name) {
        if (name == null || name.isBlank()) {
            return "untitled";
        }
        String sanitized = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (sanitized.length() > MAX_NAME_LENGTH) {
            sanitized = sanitized.substring(0, MAX_NAME_LENGTH);
        }
        return sanitized.strip();
    }

    private String sanitizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return "AgentDesk";
        }
        // 移除路径分隔符和危险字符
        String sanitized = category.replaceAll("[\\\\/:*?\"<>|]", "_");
        // 移除 .. 路径穿越
        sanitized = sanitized.replace("..", "_");
        if (sanitized.length() > MAX_NAME_LENGTH) {
            sanitized = sanitized.substring(0, MAX_NAME_LENGTH);
        }
        return sanitized.strip();
    }
}
