package top.jionjion.agentdesk.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.jionjion.agentdesk.entity.KnowledgeBase;

import java.util.List;
import java.util.Optional;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Long> {

    List<KnowledgeBase> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<KnowledgeBase> findByIdAndUserId(Long id, Long userId);

    @Query("SELECT kb.id FROM KnowledgeBase kb WHERE kb.userId = :userId AND kb.status = :status")
    List<Long> findIdsByUserIdAndStatus(@Param("userId") Long userId, @Param("status") String status);

    long countByUserId(Long userId);
}
