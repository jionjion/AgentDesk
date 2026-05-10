package top.jionjion.agentdesk.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import top.jionjion.agentdesk.dto.knowledge.RetrievalResultDto;
import top.jionjion.agentdesk.entity.KnowledgeBase;
import top.jionjion.agentdesk.entity.KnowledgeDocument;
import top.jionjion.agentdesk.repository.KnowledgeBaseRepository;
import top.jionjion.agentdesk.repository.KnowledgeDocumentRepository;

import java.io.InputStream;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库核心服务
 *
 * @author Jion
 */
@Service
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    private final KnowledgeBaseRepository kbRepo;
    private final KnowledgeDocumentRepository docRepo;
    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingService embeddingService;
    private final DocumentParser documentParser;
    private final int chunkSize;
    private final int chunkOverlap;

    public KnowledgeService(
            KnowledgeBaseRepository kbRepo,
            KnowledgeDocumentRepository docRepo,
            JdbcTemplate jdbcTemplate,
            EmbeddingService embeddingService,
            DocumentParser documentParser,
            @Value("${agentdesk.knowledge.chunk-size:512}") int chunkSize,
            @Value("${agentdesk.knowledge.chunk-overlap:50}") int chunkOverlap) {
        this.kbRepo = kbRepo;
        this.docRepo = docRepo;
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingService = embeddingService;
        this.documentParser = documentParser;
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    // ─── 知识库 CRUD ───

    public List<KnowledgeBase> listBases(Long userId) {
        return kbRepo.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public KnowledgeBase createBase(Long userId, String name, String description) {
        KnowledgeBase kb = new KnowledgeBase(userId, name, description);
        return kbRepo.save(kb);
    }

    @Transactional
    public void deleteBase(Long userId, Long kbId) {
        KnowledgeBase kb = kbRepo.findByIdAndUserId(kbId, userId)
                .orElseThrow(() -> new RuntimeException("知识库不存在"));
        // 先删除 chunks, 再删除 documents, 最后删除 knowledge_base
        jdbcTemplate.update("DELETE FROM agent_desk.knowledge_chunks WHERE kb_id = ?", kbId);
        jdbcTemplate.update("DELETE FROM agent_desk.knowledge_documents WHERE kb_id = ?", kbId);
        kbRepo.delete(kb);
    }

    // ─── 文档管理 ───

    public List<KnowledgeDocument> listDocuments(Long userId, Long kbId) {
        kbRepo.findByIdAndUserId(kbId, userId)
                .orElseThrow(() -> new RuntimeException("知识库不存在"));
        return docRepo.findByKbIdOrderByCreatedAtDesc(kbId);
    }

    @Transactional
    public KnowledgeDocument uploadDocument(Long userId, Long kbId, MultipartFile file) {
        kbRepo.findByIdAndUserId(kbId, userId)
                .orElseThrow(() -> new RuntimeException("知识库不存在"));

        KnowledgeDocument doc = new KnowledgeDocument(
                kbId, userId, file.getOriginalFilename(), file.getSize(), file.getContentType()
        );
        return docRepo.save(doc);
    }

    /**
     * 异步处理文档: 解析 → 分块 → 向量化 → 存储
     */
    @Async("knowledgeTaskExecutor")
    public void processDocumentAsync(Long docId, byte[] fileBytes, String fileName) {
        KnowledgeDocument doc = docRepo.findById(docId).orElse(null);
        if (doc == null) return;

        doc.setStatus("processing");
        doc.setUpdatedAt(System.currentTimeMillis());
        docRepo.save(doc);

        try (InputStream is = new java.io.ByteArrayInputStream(fileBytes)) {
            // 1. 文档解析
            String text = documentParser.parse(is, fileName);
            if (text.isBlank()) {
                throw new RuntimeException("文档内容为空, 无法索引");
            }
            doc.setCharCount(text.length());

            // 2. 文本分块
            List<String> chunks = TextChunker.split(text, chunkSize, chunkOverlap);
            log.info("文档 {} 分割为 {} 个块", fileName, chunks.size());

            if (chunks.isEmpty()) {
                throw new RuntimeException("文档分块结果为空");
            }

            // 3. 批量向量化
            List<float[]> embeddings = embeddingService.batchEmbed(chunks);
            log.info("文档 {} 向量化完成, {} 个向量", fileName, embeddings.size());

            // 4. 批量存储
            batchInsertChunks(doc.getId(), doc.getKbId(), doc.getUserId(), chunks, embeddings);

            // 5. 更新文档状态
            doc.setChunkCount(chunks.size());
            doc.setStatus("done");
            doc.setUpdatedAt(System.currentTimeMillis());
            docRepo.save(doc);

            // 6. 更新知识库统计
            updateKbCounts(doc.getKbId());

            log.info("文档处理完成: {} ({} 块)", fileName, chunks.size());

        } catch (Exception e) {
            log.error("文档处理失败: docId={}, fileName={}", docId, fileName, e);
            doc.setStatus("failed");
            doc.setErrorMessage(e.getMessage() != null ? e.getMessage().substring(0, Math.min(500, e.getMessage().length())) : "未知错误");
            doc.setUpdatedAt(System.currentTimeMillis());
            docRepo.save(doc);
        }
    }

    private void batchInsertChunks(Long docId, Long kbId, Long userId,
                                   List<String> chunks, List<float[]> embeddings) {
        String sql = """
                INSERT INTO agent_desk.knowledge_chunks
                    (doc_id, kb_id, user_id, chunk_index, content, token_count, metadata, embedding, created_at)
                VALUES (?, ?, ?, ?, ?, ?, '{}'::jsonb, CAST(? AS public.vector), ?)
                """;

        long now = System.currentTimeMillis();

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                String content = chunks.get(i);
                float[] embedding = embeddings.get(i);
                int tokenCount = estimateTokens(content);
                String vectorStr = vectorToString(embedding);

                ps.setLong(1, docId);
                ps.setLong(2, kbId);
                ps.setLong(3, userId);
                ps.setInt(4, i);
                ps.setString(5, content);
                ps.setInt(6, tokenCount);
                ps.setObject(7, vectorStr, Types.OTHER);
                ps.setLong(8, now);
            }

            @Override
            public int getBatchSize() {
                return chunks.size();
            }
        });
    }

    private void updateKbCounts(Long kbId) {
        jdbcTemplate.update("""
                UPDATE agent_desk.knowledge_bases
                SET doc_count = (SELECT COUNT(*) FROM agent_desk.knowledge_documents WHERE kb_id = ? AND status = 'done'),
                    chunk_count = (SELECT COUNT(*) FROM agent_desk.knowledge_chunks WHERE kb_id = ?),
                    updated_at = ?
                WHERE id = ?
                """, kbId, kbId, System.currentTimeMillis(), kbId);
    }

    @Transactional
    public void deleteDocument(Long userId, Long kbId, Long docId) {
        KnowledgeDocument doc = docRepo.findById(docId)
                .orElseThrow(() -> new RuntimeException("文档不存在"));
        if (!doc.getUserId().equals(userId) || !doc.getKbId().equals(kbId)) {
            throw new RuntimeException("无权操作");
        }
        // 先删除分块, 再删除文档
        jdbcTemplate.update("DELETE FROM agent_desk.knowledge_chunks WHERE doc_id = ?", docId);
        docRepo.delete(doc);
        updateKbCounts(kbId);
    }

    // ─── 知识检索 ───

    /**
     * 相似度检索
     */
    public List<RetrievalResultDto> retrieve(Long userId, String query, int topK, double scoreThreshold) {
        List<Long> kbIds = kbRepo.findIdsByUserIdAndStatus(userId, "active");
        if (kbIds.isEmpty()) {
            return List.of();
        }
        return doRetrieve(userId, query, kbIds, topK, scoreThreshold);
    }

    /**
     * 从指定知识库中检索
     */
    public List<RetrievalResultDto> retrieveFromBases(Long userId, String query, List<Long> kbIds, int topK, double scoreThreshold) {
        if (kbIds.isEmpty()) {
            return List.of();
        }
        return doRetrieve(userId, query, kbIds, topK, scoreThreshold);
    }

    private List<RetrievalResultDto> doRetrieve(Long userId, String query, List<Long> kbIds, int topK, double scoreThreshold) {
        log.info("知识库检索开始: userId={}, query='{}', kbIds={}, topK={}, threshold={}",
                userId, query.length() > 50 ? query.substring(0, 50) + "..." : query, kbIds, topK, scoreThreshold);

        float[] queryVector = embeddingService.embedQuery(query);
        String vectorStr = vectorToString(queryVector);

        // 验证向量非零
        boolean allZero = true;
        for (float v : queryVector) {
            if (v != 0.0f) { allZero = false; break; }
        }
        if (allZero) {
            log.warn("查询向量全为0, embedding 可能失败!");
            return List.of();
        }

        String placeholders = kbIds.stream().map(String::valueOf).collect(Collectors.joining(","));

        // 注意: JDBC URL 设置 currentSchema=agent_desk, search_path 不含 public,
        // 需要用 OPERATOR(public.<=>) 显式引用 pgvector 运算符;
        // 向量字面量内联到 SQL (由 EmbeddingService 生成, 不存在注入风险)
        String sql = """
                SELECT kc.id, kc.content, kd.file_name, kc.chunk_index,
                       1 - (kc.embedding OPERATOR(public.<=>) '%s'::public.vector) as score
                FROM agent_desk.knowledge_chunks kc
                JOIN agent_desk.knowledge_documents kd ON kc.doc_id = kd.id
                WHERE kc.kb_id IN (%s)
                  AND kc.user_id = ?
                ORDER BY kc.embedding OPERATOR(public.<=>) '%s'::public.vector
                LIMIT ?
                """.formatted(vectorStr, placeholders, vectorStr);

        List<RetrievalResultDto> results = jdbcTemplate.query(sql,
                new Object[]{userId, topK},
                new int[]{Types.BIGINT, Types.INTEGER},
                (rs, rowNum) -> new RetrievalResultDto(
                        rs.getLong("id"),
                        rs.getString("content"),
                        rs.getDouble("score"),
                        rs.getString("file_name"),
                        rs.getInt("chunk_index")
                )
        );

        log.info("知识库检索结果: 原始命中={}, 分数=[{}]",
                results.size(),
                results.stream().map(r -> String.format("%.4f", r.score())).collect(Collectors.joining(", ")));

        List<RetrievalResultDto> filtered = results.stream()
                .filter(r -> r.score() >= scoreThreshold)
                .toList();

        log.info("阈值过滤后: {} 条 (threshold={})", filtered.size(), scoreThreshold);
        return filtered;
    }

    // ─── 工具方法 ───

    private String vectorToString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            // 使用 BigDecimal 避免科学计数法 (如 1.7E-4), pgvector 不识别该格式
            sb.append(java.math.BigDecimal.valueOf(vector[i]).toPlainString());
        }
        sb.append("]");
        return sb.toString();
    }

    private int estimateTokens(String text) {
        int chineseChars = 0;
        int otherChars = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                chineseChars++;
            } else {
                otherChars++;
            }
        }
        return (int) (chineseChars * 1.5 + otherChars * 0.3);
    }
}
