package top.jionjion.agentdesk.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.jionjion.agentdesk.entity.MemoryRevision;

import java.util.List;
import java.util.Optional;

public interface MemoryRevisionRepository extends JpaRepository<MemoryRevision, Long> {
    List<MemoryRevision> findByMemoryIdAndUserIdOrderByCreatedAtDesc(Long memoryId, Long userId);
    Optional<MemoryRevision> findByIdAndMemoryIdAndUserId(Long id, Long memoryId, Long userId);
}
