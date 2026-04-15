package top.jionjion.agentdesk.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;
import top.jionjion.agentdesk.entity.ScheduledTask;
import top.jionjion.agentdesk.repository.ScheduledTaskRepository;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 动态任务调度器: 管理 cron 任务的注册、取消和重新调度
 *
 * @author Jion
 */
@Component
public class DynamicTaskScheduler {

    private static final Logger log = LoggerFactory.getLogger(DynamicTaskScheduler.class);

    private final ThreadPoolTaskScheduler taskScheduler;
    private final ScheduledTaskRepository taskRepository;
    private final ScheduledTaskExecutor taskExecutor;

    private final ConcurrentHashMap<Long, ScheduledFuture<?>> scheduledFutures = new ConcurrentHashMap<>();

    public DynamicTaskScheduler(ThreadPoolTaskScheduler taskScheduler,
                                ScheduledTaskRepository taskRepository,
                                ScheduledTaskExecutor taskExecutor) {
        this.taskScheduler = taskScheduler;
        this.taskRepository = taskRepository;
        this.taskExecutor = taskExecutor;
    }

    /**
     * 应用启动后加载所有已启用的定时任务
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        List<ScheduledTask> enabledTasks = taskRepository.findByEnabledTrue();
        for (ScheduledTask task : enabledTasks) {
            schedule(task);
        }
        log.info("定时任务调度器已启动, 加载了 {} 个任务", enabledTasks.size());
    }

    /**
     * 注册一个 cron 定时任务
     */
    public void schedule(ScheduledTask task) {
        if (task.getId() == null || !task.isEnabled()) {
            return;
        }
        try {
            CronTrigger trigger = new CronTrigger(task.getCronExpression());
            ScheduledFuture<?> future = taskScheduler.schedule(
                    () -> taskExecutor.execute(task.getId(), task.getUserId()),
                    trigger
            );
            scheduledFutures.put(task.getId(), future);
            log.info("已注册定时任务: id={}, name={}, cron={}", task.getId(), task.getName(), task.getCronExpression());
        } catch (IllegalArgumentException e) {
            log.error("定时任务 cron 表达式无效: id={}, cron={}, error={}", task.getId(), task.getCronExpression(), e.getMessage());
        }
    }

    /**
     * 取消一个定时任务
     */
    public void unschedule(Long taskId) {
        ScheduledFuture<?> future = scheduledFutures.remove(taskId);
        if (future != null) {
            future.cancel(false);
            log.info("已取消定时任务: id={}", taskId);
        }
    }

    /**
     * 重新调度: 取消旧的, 从数据库重新加载并注册
     */
    public void reschedule(Long taskId) {
        unschedule(taskId);
        taskRepository.findById(taskId).ifPresent(task -> {
            if (task.isEnabled()) {
                schedule(task);
            }
        });
    }
}
