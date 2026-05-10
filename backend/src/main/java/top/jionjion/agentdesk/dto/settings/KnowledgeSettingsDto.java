package top.jionjion.agentdesk.dto.settings;

/**
 * 知识库检索设置 DTO
 *
 * @param enabled        是否启用自动知识库检索
 * @param topK           检索返回的最大结果数
 * @param scoreThreshold 最小相似度阈值 (0.0 ~ 1.0)
 * @author Jion
 */
public record KnowledgeSettingsDto(
        Boolean enabled,
        Integer topK,
        Double scoreThreshold
) {
    public static KnowledgeSettingsDto defaults() {
        return new KnowledgeSettingsDto(true, 5, 0.8);
    }
}
