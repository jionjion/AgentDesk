package top.jionjion.agentdesk.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import top.jionjion.agentdesk.dto.knowledge.*;
import top.jionjion.agentdesk.entity.KnowledgeBase;
import top.jionjion.agentdesk.entity.KnowledgeDocument;
import top.jionjion.agentdesk.security.UserContext;
import top.jionjion.agentdesk.service.KnowledgeService;

import java.util.List;

/**
 * 知识库管理控制器
 *
 * @author Jion
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeController.class);

    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    // ─── 知识库管理 ───

    @GetMapping("/bases")
    public List<KnowledgeBaseDto> listBases() {
        Long userId = UserContext.getUserId();
        return knowledgeService.listBases(userId).stream()
                .map(this::toDto)
                .toList();
    }

    @PostMapping("/bases")
    public KnowledgeBaseDto createBase(@RequestBody CreateKnowledgeBaseRequest req) {
        Long userId = UserContext.getUserId();
        if (req.name() == null || req.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "名称不能为空");
        }
        KnowledgeBase kb = knowledgeService.createBase(userId, req.name().trim(), req.description());
        return toDto(kb);
    }

    @DeleteMapping("/bases/{id}")
    public ResponseEntity<Void> deleteBase(@PathVariable Long id) {
        knowledgeService.deleteBase(UserContext.getUserId(), id);
        return ResponseEntity.ok().build();
    }

    // ─── 文档管理 ───

    @GetMapping("/bases/{kbId}/documents")
    public List<KnowledgeDocumentDto> listDocuments(@PathVariable Long kbId) {
        Long userId = UserContext.getUserId();
        return knowledgeService.listDocuments(userId, kbId).stream()
                .map(this::toDocDto)
                .toList();
    }

    @PostMapping("/bases/{kbId}/documents")
    public KnowledgeDocumentDto uploadDocument(@PathVariable Long kbId,
                                              @RequestParam("file") MultipartFile file) {
        Long userId = UserContext.getUserId();

        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件不能超过50MB");
        }

        // 先保存文档记录
        KnowledgeDocument doc = knowledgeService.uploadDocument(userId, kbId, file);

        // 异步处理 (读取文件字节传入, 因为 MultipartFile 在请求结束后会被清理)
        try {
            byte[] fileBytes = file.getBytes();
            knowledgeService.processDocumentAsync(doc.getId(), fileBytes, file.getOriginalFilename());
        } catch (Exception e) {
            log.error("读取上传文件失败", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "文件读取失败");
        }

        return toDocDto(doc);
    }

    @DeleteMapping("/bases/{kbId}/documents/{docId}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long kbId, @PathVariable Long docId) {
        knowledgeService.deleteDocument(UserContext.getUserId(), kbId, docId);
        return ResponseEntity.ok().build();
    }

    // ─── 检索测试 ───

    @PostMapping("/retrieve")
    public List<RetrievalResultDto> retrieve(@RequestBody RetrieveRequest req) {
        Long userId = UserContext.getUserId();
        if (req.query() == null || req.query().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "查询内容不能为空");
        }
        return knowledgeService.retrieve(userId, req.query(), req.effectiveTopK(), req.effectiveThreshold());
    }

    // ─── DTO 转换 ───

    private KnowledgeBaseDto toDto(KnowledgeBase kb) {
        return new KnowledgeBaseDto(kb.getId(), kb.getName(), kb.getDescription(),
                kb.getDocCount(), kb.getChunkCount(), kb.getStatus(),
                kb.getCreatedAt(), kb.getUpdatedAt());
    }

    private KnowledgeDocumentDto toDocDto(KnowledgeDocument doc) {
        return new KnowledgeDocumentDto(doc.getId(), doc.getFileName(), doc.getFileSize(),
                doc.getContentType(), doc.getCharCount(), doc.getChunkCount(),
                doc.getStatus(), doc.getErrorMessage(), doc.getCreatedAt());
    }
}
