package top.jionjion.agentdesk.dto.knowledge;

/**
 * 创建知识库请求
 *
 * @param name        知识库名称
 * @param description 知识库描述
 * @author Jion
 */
public record CreateKnowledgeBaseRequest(
        String name,
        String description
) {
}
