package top.jionjion.agentdesk.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.jionjion.agentdesk.entity.KnowledgeBase;

import java.util.List;
import java.util.Optional;

/**
 * 知识库 — JPA 持久层
 *
 * @author Jion
 */
public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Long> {

    /**
     * 按创建时间倒序查询用户的知识库列表
     */
    List<KnowledgeBase> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * 根据 ID 和用户 ID 查询知识库
     */
    Optional<KnowledgeBase> findByIdAndUserId(Long id, Long userId);

    /**
     * 查询指定用户和状态的知识库 ID 列表
     */
    @Query("SELECT kb.id FROM KnowledgeBase kb WHERE kb.userId = :userId AND kb.status = :status")
    List<Long> findIdsByUserIdAndStatus(@Param("userId") Long userId, @Param("status") String status);

    /**
     * 统计用户的知识库数量
     */
    long countByUserId(Long userId);
}
