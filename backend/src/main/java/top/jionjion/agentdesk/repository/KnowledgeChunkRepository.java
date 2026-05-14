package top.jionjion.agentdesk.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.jionjion.agentdesk.entity.KnowledgeChunk;

/**
 * 知识库文档分块 — JPA 持久层
 *
 * @author Jion
 */
public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, Long> {

    /**
     * 统计知识库的分块数量
     */
    long countByKbId(Long kbId);

    /**
     * 根据文档 ID 删除所有分块
     */
    void deleteByDocId(Long docId);
}
