package top.jionjion.agentdesk.dto.skill;

import java.util.List;

/**
 * 社区技能元数据。
 *
 * @param id              社区中的技能 ID，例如 {@code @anthropics/skill-creator}
 * @param displayName     展示名称
 * @param description     技能描述
 * @param developer       开发者
 * @param owner           所有者
 * @param license         开源许可证
 * @param sourceUrl       上游源码地址
 * @param category        分类
 * @param tags            标签
 * @param logoUrl         图标地址
 * @param viewCount       浏览量
 * @param downloads       下载量
 * @param lastModified    最后更新时间
 * @param installCommands 社区提供的安装命令
 * @param installed       当前用户是否已经安装
 */
public record MarketplaceSkillDto(
        String id,
        String displayName,
        String description,
        String developer,
        String owner,
        String license,
        String sourceUrl,
        String category,
        List<String> tags,
        String logoUrl,
        long viewCount,
        long downloads,
        String lastModified,
        List<String> installCommands,
        boolean installed
) {
    public MarketplaceSkillDto withInstalled(boolean value) {
        return new MarketplaceSkillDto(
                id, displayName, description, developer, owner, license, sourceUrl,
                category, tags, logoUrl, viewCount, downloads, lastModified,
                installCommands, value);
    }
}
