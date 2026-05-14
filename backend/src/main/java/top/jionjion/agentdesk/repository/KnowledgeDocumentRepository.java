package top.jionjion.agentdesk.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.jionjion.agentdesk.entity.KnowledgeDocument;

import java.util.List;

/**
 * 知识库文档 — JPA 持久层
 *
 * @author Jion
 */
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    /**
     * 按创建时间倒序查询知识库下的文档列表
     */
    List<KnowledgeDocument> findByKbIdOrderByCreatedAtDesc(Long kbId);

    /**
     * 统计知识库中指定状态的文档数量
     */
    long countByKbIdAndStatus(Long kbId, String status);
}
