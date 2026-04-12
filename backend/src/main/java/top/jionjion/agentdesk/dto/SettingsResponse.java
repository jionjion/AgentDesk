package top.jionjion.agentdesk.dto;

/**
 * 设置响应
 *
 * @author Jion
 */
public record SettingsResponse(ProfileDto profile, ModelSettingsDto model, AppSettingsDto app) {
}
