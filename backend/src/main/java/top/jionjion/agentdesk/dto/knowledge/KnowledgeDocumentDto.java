package top.jionjion.agentdesk.dto.knowledge;

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
