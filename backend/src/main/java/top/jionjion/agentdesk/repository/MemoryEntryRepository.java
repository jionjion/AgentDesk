package top.jionjion.agentdesk.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.jionjion.agentdesk.entity.MemoryEntry;

import java.util.List;
import java.util.Optional;

public interface MemoryEntryRepository extends JpaRepository<MemoryEntry, Long> {
    List<MemoryEntry> findByUserIdAndStatusOrderByPinnedDescUpdatedAtDesc(Long userId, String status);
    List<MemoryEntry> findByUserIdOrderByPinnedDescUpdatedAtDesc(Long userId);
    List<MemoryEntry> findTop500ByUserIdAndStatusOrderByPinnedDescUpdatedAtDesc(Long userId, String status);
    List<MemoryEntry> findTop100ByStatusAndProviderRefIsNotNullOrderByUpdatedAtAsc(String status);
    List<MemoryEntry> findTop100ByStatusAndProviderSyncPendingTrueAndProviderRefIsNotNullOrderByUpdatedAtAsc(
            String status);
    Optional<MemoryEntry> findByIdAndUserId(Long id, Long userId);
    List<MemoryEntry> findByUserIdAndScopeTypeAndScopeIdAndStatus(
            Long userId, String scopeType, String scopeId, String status);
    List<MemoryEntry> findByUserIdAndScopeTypeAndScopeIdAndSubjectKeyAndStatusIn(
            Long userId, String scopeType, String scopeId, String subjectKey, List<String> statuses);
    List<MemoryEntry> findByStatusAndValidUntilLessThanEqual(String status, Long now);
    long countByUserIdAndStatus(Long userId, String status);
    @Query("SELECT m FROM MemoryEntry m WHERE m.userId = :userId AND m.scopeType = :scopeType " +
            "AND ((:scopeId IS NULL AND m.scopeId IS NULL) OR m.scopeId = CAST(:scopeId AS string)) " +
            "AND m.contentHash = :contentHash AND m.status = 'ACTIVE'")
    Optional<MemoryEntry> findActiveDuplicate(@Param("userId") Long userId,
                                              @Param("scopeType") String scopeType,
                                              @Param("scopeId") String scopeId,
                                              @Param("contentHash") String contentHash);
}
