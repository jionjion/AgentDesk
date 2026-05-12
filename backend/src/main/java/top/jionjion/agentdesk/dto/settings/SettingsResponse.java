package top.jionjion.agentdesk.dto.settings;

import top.jionjion.agentdesk.dto.auth.ProfileDto;

/**
 * 设置响应
 *
 * @param profile  用户资料
 * @param model    模型设置
 * @param app      应用设置
 * @param memory   记忆设置
 * @param obsidian Obsidian 设置
 * @author Jion
 */
public record SettingsResponse(ProfileDto profile, ModelSettingsDto model, AppSettingsDto app,
                               MemorySettingsDto memory, ObsidianSettingsDto obsidian) {
}
