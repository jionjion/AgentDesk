package top.jionjion.agentdesk.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import top.jionjion.agentdesk.dto.FileResponse;
import top.jionjion.agentdesk.entity.FileRecord;
import top.jionjion.agentdesk.repository.FileRecordRepository;
import top.jionjion.agentdesk.security.UserContext;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * 文件业务服务
 *
 * @author Jion
 */
@Service
public class FileService {

    private final OssService ossService;
    private final FileRecordRepository fileRecordRepository;

    public FileService(OssService ossService, FileRecordRepository fileRecordRepository) {
        this.ossService = ossService;
        this.fileRecordRepository = fileRecordRepository;
    }

    /**
     * 上传文件, sessionId 可为空(通用上传)
     */
    public FileResponse upload(MultipartFile file, String sessionId) {
        Long userId = UserContext.getUserId();
        String originalName = file.getOriginalFilename();
        String prefix = sessionId != null ? "chat/" + sessionId
                : "general/" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = prefix + "/" + System.currentTimeMillis() + "_"
                + UUID.randomUUID().toString().substring(0, 8) + "_" + originalName;

        try {
            ossService.upload(key, file.getInputStream(), file.getSize(), file.getContentType());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "文件上传失败");
        }

        FileRecord record = new FileRecord(originalName, key, file.getContentType(), file.getSize(), sessionId);
        record.setUserId(userId);
        fileRecordRepository.save(record);

        return toResponse(record);
    }

    /**
     * 获取文件信息 + 预签名下载 URL
     */
    public FileResponse getFile(Long id) {
        Long userId = UserContext.getUserId();
        FileRecord record = fileRecordRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在"));
        return toResponse(record);
    }

    /**
     * 删除文件(OSS + 数据库)
     */
    public void delete(Long id) {
        Long userId = UserContext.getUserId();
        FileRecord record = fileRecordRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在"));
        ossService.delete(record.getOssKey());
        fileRecordRepository.delete(record);
    }

    /**
     * 列出会话下所有文件
     */
    public List<FileResponse> listBySession(String sessionId) {
        Long userId = UserContext.getUserId();
        return fileRecordRepository.findBySessionIdAndUserIdOrderByCreatedAtAsc(sessionId, userId)
                .stream().map(this::toResponse).toList();
    }

    /**
     * 按 ID 批量查询文件
     */
    public List<FileResponse> getByIds(List<Long> ids) {
        return fileRecordRepository.findByIdIn(ids)
                .stream().map(this::toResponse).toList();
    }

    /**
     * 读取图片文件并返回 Base64 编码字符串 (备选方案: 当 OSS 预签名 URL 不可用时使用)
     */
    public String readImageAsBase64(Long fileId) {
        Long userId = UserContext.getUserId();
        FileRecord record = fileRecordRepository.findByIdAndUserId(fileId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在"));
        byte[] bytes = ossService.readAsBytes(record.getOssKey());
        return Base64.getEncoder().encodeToString(bytes);
    }

    private FileResponse toResponse(FileRecord r) {
        String downloadUrl = ossService.generatePresignedUrl(r.getOssKey(), 60);
        return new FileResponse(r.getId(), r.getOriginalName(), r.getContentType(),
                r.getSize(), r.getSessionId(), downloadUrl, r.getCreatedAt());
    }
}
