package top.jionjion.agentdesk.dto;

import java.util.List;

/**
 * 技能定义 DTO — 前端上传的技能定义（从 YAML 转换而来）
 *
 * @param id          技能唯一标识, 小写字母+数字+连字符
 * @param name        显示名称
 * @param description 技能描述, 也是 Agent 委派依据
 * @param author      作者
 * @param version     版本号
 * @param category    分类: writing/coding/data/file/other
 * @param tags        标签列表
 * @param icon        lucide 图标名
 * @param bgColor     背景色
 * @param systemPrompt 子代理系统提示词
 * @param maxIters    ReAct 最大迭代次数
 * @param tools       工具类名列表, 如 ["FileTools", "CalculateTools"]
 * @author Jion
 */
public record SkillDefinitionDto(
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
        Integer maxIters,
        List<String> tools
) {
}
