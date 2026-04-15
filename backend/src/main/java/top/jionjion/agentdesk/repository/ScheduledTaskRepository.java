package top.jionjion.agentdesk.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.jionjion.agentdesk.entity.ScheduledTask;

import java.util.List;

/**
 * 定时任务 Repository
 *
 * @author Jion
 */
public interface ScheduledTaskRepository extends JpaRepository<ScheduledTask, Long> {

    List<ScheduledTask> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<ScheduledTask> findByEnabledTrue();
}
