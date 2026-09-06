package top.jionjion.agentdesk.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.jionjion.agentdesk.entity.MemoryJob;

import java.util.List;

public interface MemoryJobRepository extends JpaRepository<MemoryJob, Long> {
    boolean existsByIdempotencyKey(String idempotencyKey);
    List<MemoryJob> findByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            List<String> statuses, long now, Pageable pageable);
    List<MemoryJob> findByUserIdAndUserMessageIdIn(Long userId, List<Long> userMessageIds);
    List<MemoryJob> findTop100ByUserIdAndStatusOrderByUpdatedAtDesc(Long userId, String status);
    List<MemoryJob> findTop100ByUserIdOrderByUpdatedAtDesc(Long userId);
    java.util.Optional<MemoryJob> findByIdAndUserId(Long id, Long userId);
    long countByUserIdAndStatus(Long userId, String status);
    List<MemoryJob> findByUserIdAndStatusIn(Long userId, List<String> statuses);

    @Modifying
    @Query(value = """
            INSERT INTO agent_desk.memory_jobs
                (version, user_id, session_id, project_id, user_message_id, user_message,
                 status, attempts, next_attempt_at, idempotency_key, created_at, updated_at)
            VALUES
                (0, :userId, :sessionId, :projectId, :userMessageId, NULL,
                 'PENDING', 0, :now, :idempotencyKey, :now, :now)
            ON CONFLICT (idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int insertPending(@Param("userId") Long userId,
                      @Param("sessionId") String sessionId,
                      @Param("projectId") String projectId,
                      @Param("userMessageId") Long userMessageId,
                      @Param("idempotencyKey") String idempotencyKey,
                      @Param("now") long now);
}
