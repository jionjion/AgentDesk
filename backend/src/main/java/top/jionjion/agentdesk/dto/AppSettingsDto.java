package top.jionjion.agentdesk.dto;

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
