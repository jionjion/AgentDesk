package top.jionjion.agentdesk.dto.knowledge;

/**
 * 知识库检索结果 DTO
 *
 * @param chunkId      分块ID
 * @param content      分块内容
 * @param score        相似度分数
 * @param documentName 所属文档名称
 * @param chunkIndex   分块索引
 * @author Jion
 */
public record RetrievalResultDto(
        Long chunkId,
        String content,
        double score,
        String documentName,
        int chunkIndex
) {
}
