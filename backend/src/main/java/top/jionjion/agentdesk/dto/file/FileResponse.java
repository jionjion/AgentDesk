package top.jionjion.agentdesk.dto.file;

/**
 * 文件信息响应
 *
 * @param id           文件ID
 * @param originalName 原始文件名
 * @param contentType  文件MIME类型
 * @param size         文件大小(字节)
 * @param sessionId    所属会话ID
 * @param downloadUrl  下载地址
 * @param createdAt    创建时间戳
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
