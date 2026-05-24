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

    /**
     * 根据用户ID按创建时间倒序查询定时任务
     *
     * @param userId 用户ID
     * @return 定时任务列表
     */
    List<ScheduledTask> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * 查询所有启用的定时任务
     *
     * @return 定时任务列表
     */
    List<ScheduledTask> findByEnabledTrue();
}
