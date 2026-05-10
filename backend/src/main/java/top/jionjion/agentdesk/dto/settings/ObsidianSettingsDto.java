package top.jionjion.agentdesk.dto.settings;

/**
 * Obsidian 知识沉淀设置 DTO
 *
 * @param vaultPath              Obsidian Vault 本地路径
 * @param autoExportOnSessionEnd 会话切换时是否自动沉淀
 * @param defaultCategory        默认分类目录名
 * @author Jion
 */
public record ObsidianSettingsDto(
        String vaultPath,
        Boolean autoExportOnSessionEnd,
        String defaultCategory
) {
    public static ObsidianSettingsDto defaults() {
        return new ObsidianSettingsDto(null, false, "AgentDesk");
    }
}
