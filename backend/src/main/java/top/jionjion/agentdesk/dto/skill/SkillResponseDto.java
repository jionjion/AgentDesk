package top.jionjion.agentdesk.dto.skill;

import java.util.List;

/**
 * 技能响应 DTO — 返回前端, 包含启用状态和技能类型
 *
 * @param id           技能唯一标识
 * @param name         显示名称
 * @param description  技能描述
 * @param author       作者
 * @param version      版本号
 * @param category     分类
 * @param tags         标签列表
 * @param icon         lucide 图标名
 * @param bgColor      背景色
 * @param systemPrompt 子代理系统提示词
 * @param maxIters     ReAct 最大迭代次数
 * @param tools        工具类名列表
 * @param builtin      是否为内置技能
 * @param enabled      是否启用
 * @param skillType    技能类型
 * @param installPath  安装路径
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
