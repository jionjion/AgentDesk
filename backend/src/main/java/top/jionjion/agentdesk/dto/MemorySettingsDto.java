package top.jionjion.agentdesk.dto;

/**
 * 长期记忆设置 DTO
 * <p>
 * 仅包含用户可控的开关。Mem0 服务地址和 API Key 由后端配置文件管理。
 *
 * @param enabled 是否启用长期记忆
 * @author Jion
 */
public record MemorySettingsDto(
        Boolean enabled
) {
    public static MemorySettingsDto defaults() {
        return new MemorySettingsDto(false);
    }
}
