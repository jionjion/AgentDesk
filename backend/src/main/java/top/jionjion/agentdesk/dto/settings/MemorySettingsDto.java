package top.jionjion.agentdesk.dto.settings;

/**
 * 长期记忆设置 DTO
 * <p>
 * 仅包含用户可控的开关。Mem0 服务地址和 API Key 由后端配置文件管理。
 *
 * @param enabled 是否启用长期记忆
 * @author Jion
 */
public record MemorySettingsDto(
        Integer schemaVersion,
        Boolean enabled,
        Boolean autoLearningEnabled,
        Boolean projectMemoryEnabled,
        Boolean showMemorySources,
        String sensitiveMemoryPolicy
) {
    public MemorySettingsDto {
        schemaVersion = schemaVersion != null ? schemaVersion : 2;
        enabled = enabled != null ? enabled : false;
        autoLearningEnabled = autoLearningEnabled != null ? autoLearningEnabled : true;
        projectMemoryEnabled = projectMemoryEnabled != null ? projectMemoryEnabled : true;
        showMemorySources = showMemorySources != null ? showMemorySources : true;
        sensitiveMemoryPolicy = "EXPLICIT_ONLY";
    }

    public MemorySettingsDto(Boolean enabled, Boolean autoLearningEnabled,
                             Boolean projectMemoryEnabled, Boolean showMemorySources) {
        this(2, enabled, autoLearningEnabled, projectMemoryEnabled, showMemorySources, "EXPLICIT_ONLY");
    }

    public static MemorySettingsDto defaults() {
        return new MemorySettingsDto(2, false, true, true, true, "EXPLICIT_ONLY");
    }
}
