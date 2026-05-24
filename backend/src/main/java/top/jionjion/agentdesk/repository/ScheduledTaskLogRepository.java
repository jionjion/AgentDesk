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

    /**
     * 根据用户ID按开始时间倒序查询任务执行日志
     *
     * @param userId 用户ID
     * @return 任务执行日志列表
     */
    List<ScheduledTaskLog> findByUserIdOrderByStartedAtDesc(Long userId);

    /**
     * 根据任务ID按开始时间倒序查询任务执行日志
     *
     * @param taskId 任务ID
     * @return 任务执行日志列表
     */
    List<ScheduledTaskLog> findByTaskIdOrderByStartedAtDesc(Long taskId);
}
