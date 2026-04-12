package top.jionjion.agentdesk.dto;

/**
 * 文件信息响应
 *
 * @author Jion
 */
public record FileResponse(
        Long id,
        String originalName,
        String contentType,
        long size,
        String sessionId,
        String downloadUrl,
        long createdAt
) {
}
