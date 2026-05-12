package top.jionjion.agentdesk.dto.knowledge;

/**
 * 知识库 DTO
 *
 * @param id          知识库ID
 * @param name        知识库名称
 * @param description 知识库描述
 * @param docCount    文档数量
 * @param chunkCount  分块数量
 * @param status      状态
 * @param createdAt   创建时间戳
 * @param updatedAt   更新时间戳
 * @author Jion
 */
public record KnowledgeBaseDto(
        Long id,
        String name,
        String description,
        int docCount,
        int chunkCount,
        String status,
        long createdAt,
        long updatedAt
) {
}
