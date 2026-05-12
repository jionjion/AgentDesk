package top.jionjion.agentdesk.dto.knowledge;

/**
 * 知识库文档 DTO
 *
 * @param id           文档ID
 * @param fileName     文件名
 * @param fileSize     文件大小(字节)
 * @param contentType  文件MIME类型
 * @param charCount    字符数
 * @param chunkCount   分块数量
 * @param status       处理状态
 * @param errorMessage 错误信息
 * @param createdAt    创建时间戳
 * @author Jion
 */
public record KnowledgeDocumentDto(
        Long id,
        String fileName,
        long fileSize,
        String contentType,
        int charCount,
        int chunkCount,
        String status,
        String errorMessage,
        long createdAt
) {
}
