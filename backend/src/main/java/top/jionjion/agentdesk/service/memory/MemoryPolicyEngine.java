package top.jionjion.agentdesk.service.memory;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Conservative policy for content that is allowed to become long-term memory. */
@Component
public class MemoryPolicyEngine {
    private static final long DAY = 86_400_000L;
    private static final Set<String> ALLOWED_CATEGORIES = Set.of(
            "PROFILE", "PREFERENCE", "WORKFLOW", "TOOL_PREFERENCE", "PROJECT_FACT",
            "DECISION", "GLOSSARY", "STAKEHOLDER", "CONSTRAINT", "ONGOING_STATE",
            "SCHEDULE", "EPISODE_SUMMARY", "OTHER");
    private static final Pattern SECRET = Pattern.compile(
            "(?is)(-----BEGIN [A-Z ]*PRIVATE KEY-----|(?:api[_ -]?key|access[_ -]?token|refresh[_ -]?token|password|passwd|secret|验证码|密码|密钥|访问令牌|恢复码)\\s*[:=：]\\s*[^\\s,;，；]{4,}|\\bsk-[A-Za-z0-9_-]{16,}|\\bAKIA[A-Z0-9]{16})");
    private static final Pattern HIGHLY_SENSITIVE = Pattern.compile(
            "(?i)(\\b\\d{17}[0-9Xx]\\b|\\b(?:\\d[ -]?){16,19}\\b|身份证|银行卡|精确住址|病历|诊断|宗教信仰|政治立场|性取向|工资|薪资|财务状况)");
    private static final Pattern UNSTABLE = Pattern.compile(
            "(?i)^(如果|假如|假设|例如|示例|举例|虚构|角色扮演)|(?:听说|据说|他说|她说|他们说|引用)|(?:可能|也许|大概).{0,20}(?:是|为|会|要)");
    private static final Pattern INSTRUCTION_LIKE = Pattern.compile(
            "(?i)(忽略.{0,12}(?:系统|之前|以上).{0,8}(?:指令|规则)|调用.{0,10}(?:工具|命令)|system prompt|developer message)");

    public Decision evaluate(String content, boolean explicit, boolean sensitiveConfirmed,
                             String requestedCategory, String projectId) {
        String clean = content == null ? "" : content.trim();
        if (clean.isBlank()) return Decision.reject("empty");
        if (SECRET.matcher(clean).find()) {
            if (explicit) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码、密钥、令牌或验证码不能保存为长期记忆");
            }
            return Decision.reject("secret_blocked");
        }
        boolean sensitive = HIGHLY_SENSITIVE.matcher(clean).find();
        if (sensitive && !explicit) return Decision.reject("sensitive_auto_blocked");
        if (sensitive && !sensitiveConfirmed) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该内容可能包含敏感信息，需要明确确认后才能保存");
        }
        if (!explicit && UNSTABLE.matcher(clean).find()) return Decision.reject("unstable_or_quoted");
        if (!explicit && INSTRUCTION_LIKE.matcher(clean).find()) return Decision.reject("instruction_like");
        if (requestedCategory != null && !requestedCategory.isBlank()
                && !ALLOWED_CATEGORIES.contains(requestedCategory.trim().toUpperCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的记忆分类");
        }

        String category = normalizeCategory(requestedCategory, clean);
        Long validUntil = defaultValidUntil(category);
        String sensitivity = sensitive ? "SENSITIVE" : "NORMAL";
        return new Decision(true, null, category, sensitivity, validUntil, subjectKey(category, clean, projectId));
    }

    /** 原始用户消息是否足够稳定, 可作为自动抽取候选的证据 (假设/转述/指令型原文不可信)。 */
    public boolean stableEvidence(String originalMessage) {
        String clean = originalMessage == null ? "" : originalMessage.trim();
        if (clean.isBlank()) return false;
        return !UNSTABLE.matcher(clean).find() && !INSTRUCTION_LIKE.matcher(clean).find();
    }

    public String normalizeCategory(String requested, String content) {
        if (requested != null && requested.matches("[A-Za-z_]{2,32}")) {
            String normalized = requested.toUpperCase(Locale.ROOT);
            if (ALLOWED_CATEGORIES.contains(normalized)) return normalized;
        }
        String value = content.toLowerCase(Locale.ROOT);
        if (value.contains("喜欢") || value.contains("偏好") || value.contains("prefer")) return "PREFERENCE";
        if (value.contains("流程") || value.contains("习惯") || value.contains("workflow")) return "WORKFLOW";
        if (value.contains("工具") || value.contains("word") || value.contains("pdf")) return "TOOL_PREFERENCE";
        if (value.contains("决定") || value.contains("采用") || value.contains("方案")) return "DECISION";
        if (value.contains("简称") || value.contains("术语") || value.contains("指的是")) return "GLOSSARY";
        if (value.contains("不得") || value.contains("必须") || value.contains("限制")) return "CONSTRAINT";
        if (value.contains("阶段") || value.contains("进行中") || value.contains("阻塞")) return "ONGOING_STATE";
        if (value.contains("项目") || value.contains("project")) return "PROJECT_FACT";
        if (value.contains("截止") || value.contains("会议") || value.contains("deadline")) return "SCHEDULE";
        if (value.contains("称呼") || value.contains("叫我") || value.contains("姓名") || value.contains("岗位")) return "PROFILE";
        return "OTHER";
    }

    private Long defaultValidUntil(String category) {
        long now = System.currentTimeMillis();
        return switch (category) {
            case "ONGOING_STATE" -> now + 30 * DAY;
            case "EPISODE_SUMMARY", "SCHEDULE" -> now + 14 * DAY;
            case "STAKEHOLDER" -> now + 180 * DAY;
            default -> null;
        };
    }

    private String subjectKey(String category, String content, String projectId) {
        String value = content.toLowerCase(Locale.ROOT);
        String key = null;
        if ("PREFERENCE".equals(category)) {
            if (value.matches(".*(中文|英文|语言|language).*")) key = "output-language";
            else if (value.matches(".*(简洁|详细|先给结论|风格|style).*")) key = "response-style";
            else if (value.matches(".*(表格|markdown|word|pdf|格式|format).*")) key = "output-format";
        } else if ("PROFILE".equals(category)) {
            if (value.matches(".*(姓名|叫我|称呼).*")) key = "display-name";
            else if (value.matches(".*(岗位|职位|职责).*")) key = "job-role";
        } else if ("ONGOING_STATE".equals(category) && value.matches(".*(阶段|进行中|开发|评审|需求|测试|上线).*")) {
            key = "project-stage";
        } else if ("TOOL_PREFERENCE".equals(category)) {
            key = "preferred-tool";
        }
        if (key == null) return null;
        return category + ":" + (projectId == null ? "USER" : projectId) + ":" + key;
    }

    public record Decision(boolean accepted, String reason, String category, String sensitivity,
                           Long defaultValidUntil, String subjectKey) {
        static Decision reject(String reason) {
            return new Decision(false, reason, null, "SECRET_BLOCKED", null, null);
        }
    }
}
