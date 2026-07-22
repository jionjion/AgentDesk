package top.jionjion.agentdesk.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.jionjion.agentdesk.entity.MemoryUsageEvent;

public interface MemoryUsageEventRepository extends JpaRepository<MemoryUsageEvent, Long> {
    long countByUserId(Long userId);
}
