package top.jionjion.agentdesk.dto.knowledge;

public record RetrievalResultDto(
        Long chunkId,
        String content,
        double score,
        String documentName,
        int chunkIndex
) {
}
