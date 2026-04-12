package top.jionjion.agentdesk.dto;

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
