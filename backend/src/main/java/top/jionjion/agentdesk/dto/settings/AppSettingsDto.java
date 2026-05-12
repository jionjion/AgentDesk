package top.jionjion.agentdesk.dto.settings;

/**
 * 应用设置 DTO
 *
 * @param theme    主题, 如 "auto"、"light"、"dark"
 * @param language 语言, 如 "zh-CN"
 * @param sendKey  发送快捷键, 如 "Enter"
 * @param fontSize 字体大小
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
