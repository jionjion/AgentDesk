package top.jionjion.agentdesk.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.jionjion.agentdesk.entity.ScheduledTaskLog;

import java.util.List;

/**
 * 定时任务执行记录 Repository
 *
 * @author Jion
 */
public interface ScheduledTaskLogRepository extends JpaRepository<ScheduledTaskLog, Long> {

    List<ScheduledTaskLog> findByUserIdOrderByStartedAtDesc(Long userId);

    List<ScheduledTaskLog> findByTaskIdOrderByStartedAtDesc(Long taskId);
}
