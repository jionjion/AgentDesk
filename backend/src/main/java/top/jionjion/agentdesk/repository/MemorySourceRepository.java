package top.jionjion.agentdesk.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.jionjion.agentdesk.entity.MemorySource;

import java.util.List;
import java.util.Optional;

public interface MemorySourceRepository extends JpaRepository<MemorySource, Long> {
    List<MemorySource> findByMemoryIdAndUserIdOrderByCreatedAtDesc(Long memoryId, Long userId);
    boolean existsByMemoryIdAndSourceTypeAndSourceId(Long memoryId, String sourceType, String sourceId);
    long countByMemoryId(Long memoryId);
    List<MemorySource> findByUserIdAndSessionId(Long userId, String sessionId);
    List<MemorySource> findByUserIdAndSourceTypeAndSourceIdIn(
            Long userId, String sourceType, List<String> sourceIds);
    Optional<MemorySource> findByIdAndMemoryIdAndUserId(Long id, Long memoryId, Long userId);
}
