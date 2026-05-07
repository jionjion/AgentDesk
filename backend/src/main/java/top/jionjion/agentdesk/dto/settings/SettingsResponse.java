package top.jionjion.agentdesk.dto.settings;

import top.jionjion.agentdesk.dto.auth.ProfileDto;

/**
 * 设置响应
 *
 * @author Jion
 */
public record SettingsResponse(ProfileDto profile, ModelSettingsDto model, AppSettingsDto app,
                               MemorySettingsDto memory) {
}
