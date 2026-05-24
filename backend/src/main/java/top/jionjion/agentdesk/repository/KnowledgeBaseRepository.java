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
     *
     * @param userId 用户ID
     * @return 知识库列表
     */
    List<KnowledgeBase> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * 根据 ID 和用户 ID 查询知识库
     *
     * @param id     知识库ID
     * @param userId 用户ID
     * @return 知识库Optional
     */
    Optional<KnowledgeBase> findByIdAndUserId(Long id, Long userId);

    /**
     * 查询指定用户和状态的知识库 ID 列表
     *
     * @param userId 用户ID
     * @param status 状态
     * @return 知识库ID列表
     */
    @Query("SELECT kb.id FROM KnowledgeBase kb WHERE kb.userId = :userId AND kb.status = :status")
    List<Long> findIdsByUserIdAndStatus(@Param("userId") Long userId, @Param("status") String status);

    /**
     * 统计用户的知识库数量
     *
     * @param userId 用户ID
     * @return 知识库数量
     */
    long countByUserId(Long userId);
}
