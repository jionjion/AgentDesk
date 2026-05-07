package top.jionjion.agentdesk.dto.settings;

/**
 * 应用设置 DTO
 *
 * @author Jion
 */
public record AppSettingsDto(
        String theme,
        String language,
        String sendKey,
        Integer fontSize
) {
    public static AppSettingsDto defaults() {
        return new AppSettingsDto("auto", "zh-CN", "Enter", 14);
    }
}
