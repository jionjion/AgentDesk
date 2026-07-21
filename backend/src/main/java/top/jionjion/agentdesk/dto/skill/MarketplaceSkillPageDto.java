package top.jionjion.agentdesk.dto.skill;

import java.util.List;

/** 社区技能分页结果。 */
public record MarketplaceSkillPageDto(
        List<MarketplaceSkillDto> skills,
        long total,
        int pageNumber,
        int pageSize
) {
}
