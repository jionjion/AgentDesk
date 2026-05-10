package top.jionjion.agentdesk.dto.skill;

import java.util.List;

/**
 * 技能响应 DTO — 返回前端, 包含启用状态和技能类型
 *
 * @author Jion
 */
public record SkillResponseDto(
        String id,
        String name,
        String description,
        String author,
        String version,
        String category,
        List<String> tags,
        String icon,
        String bgColor,
        String systemPrompt,
        int maxIters,
        List<String> tools,
        boolean builtin,
        boolean enabled,
        String skillType,
        String installPath
) {
}
