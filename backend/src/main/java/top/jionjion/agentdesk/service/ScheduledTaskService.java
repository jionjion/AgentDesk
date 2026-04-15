package top.jionjion.agentdesk.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import top.jionjion.agentdesk.dto.ScheduledTaskLogResponse;
import top.jionjion.agentdesk.dto.ScheduledTaskRequest;
import top.jionjion.agentdesk.dto.ScheduledTaskResponse;
import top.jionjion.agentdesk.entity.ScheduledTask;
import top.jionjion.agentdesk.entity.ScheduledTaskLog;
import top.jionjion.agentdesk.repository.ScheduledTaskLogRepository;
import top.jionjion.agentdesk.repository.ScheduledTaskRepository;
import top.jionjion.agentdesk.scheduler.DynamicTaskScheduler;
import top.jionjion.agentdesk.scheduler.ScheduledTaskExecutor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 定时任务服务
 *
 * @author Jion
 */
@Service
public class ScheduledTaskService {

    private final ScheduledTaskRepository taskRepository;
    private final ScheduledTaskLogRepository logRepository;
    private final DynamicTaskScheduler dynamicTaskScheduler;
    private final ScheduledTaskExecutor taskExecutor;

    public ScheduledTaskService(ScheduledTaskRepository taskRepository,
                                ScheduledTaskLogRepository logRepository,
                                DynamicTaskScheduler dynamicTaskScheduler,
                                ScheduledTaskExecutor taskExecutor) {
        this.taskRepository = taskRepository;
        this.logRepository = logRepository;
        this.dynamicTaskScheduler = dynamicTaskScheduler;
        this.taskExecutor = taskExecutor;
    }

    /**
     * 列出用户的所有定时任务
     */
    public List<ScheduledTaskResponse> listTasks(Long userId) {
        return taskRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 获取单个定时任务
     */
    public ScheduledTaskResponse getTask(Long taskId, Long userId) {
        ScheduledTask task = findOwnedTask(taskId, userId);
        return toResponse(task);
    }

    /**
     * 创建定时任务
     */
    @Transactional
    public ScheduledTaskResponse createTask(ScheduledTaskRequest req, Long userId) {
        validateRequest(req);
        validateCronExpression(req.cronExpression());

        long now = System.currentTimeMillis();
        ScheduledTask task = new ScheduledTask();
        task.setUserId(userId);
        task.setName(req.name());
        task.setDescription(req.description());
        task.setPrompt(req.prompt());
        task.setCronExpression(req.cronExpression());
        task.setScheduleLabel(req.scheduleLabel());
        task.setSkillId(req.skillId());
        task.setEnabled(false);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);

        taskRepository.save(task);
        return toResponse(task);
    }

    /**
     * 更新定时任务
     */
    @Transactional
    public ScheduledTaskResponse updateTask(Long taskId, ScheduledTaskRequest req, Long userId) {
        validateRequest(req);
        validateCronExpression(req.cronExpression());

        ScheduledTask task = findOwnedTask(taskId, userId);
        task.setName(req.name());
        task.setDescription(req.description());
        task.setPrompt(req.prompt());
        task.setCronExpression(req.cronExpression());
        task.setScheduleLabel(req.scheduleLabel());
        task.setSkillId(req.skillId());
        task.setUpdatedAt(System.currentTimeMillis());

        taskRepository.save(task);

        if (task.isEnabled()) {
            dynamicTaskScheduler.reschedule(taskId);
        }

        return toResponse(task);
    }

    /**
     * 删除定时任务
     */
    @Transactional
    public void deleteTask(Long taskId, Long userId) {
        ScheduledTask task = findOwnedTask(taskId, userId);
        dynamicTaskScheduler.unschedule(taskId);
        taskRepository.delete(task);
    }

    /**
     * 启用/禁用定时任务
     */
    @Transactional
    public void setEnabled(Long taskId, boolean enabled, Long userId) {
        ScheduledTask task = findOwnedTask(taskId, userId);
        task.setEnabled(enabled);
        task.setUpdatedAt(System.currentTimeMillis());
        taskRepository.save(task);
        dynamicTaskScheduler.reschedule(taskId);
    }

    /**
     * 手动立即执行
     */
    public void runTaskNow(Long taskId, Long userId) {
        findOwnedTask(taskId, userId);
        CompletableFuture.runAsync(() -> taskExecutor.execute(taskId, userId));
    }

    /**
     * 获取用户全部执行记录
     */
    public List<ScheduledTaskLogResponse> getLogs(Long userId) {
        Map<Long, String> taskNameMap = taskRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .collect(Collectors.toMap(ScheduledTask::getId, ScheduledTask::getName));

        return logRepository.findByUserIdOrderByStartedAtDesc(userId)
                .stream()
                .map(log -> toLogResponse(log, taskNameMap.getOrDefault(log.getTaskId(), "已删除任务")))
                .toList();
    }

    /**
     * 获取单个任务的执行记录
     */
    public List<ScheduledTaskLogResponse> getLogsByTaskId(Long taskId, Long userId) {
        ScheduledTask task = findOwnedTask(taskId, userId);
        return logRepository.findByTaskIdOrderByStartedAtDesc(taskId)
                .stream()
                .map(log -> toLogResponse(log, task.getName()))
                .toList();
    }

    // === Private helpers ===

    private ScheduledTask findOwnedTask(Long taskId, Long userId) {
        ScheduledTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "定时任务不存在"));
        if (!task.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该定时任务");
        }
        return task;
    }

    private void validateRequest(ScheduledTaskRequest req) {
        if (req.name() == null || req.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "任务名称不能为空");
        }
        if (req.prompt() == null || req.prompt().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "任务提示词不能为空");
        }
        if (req.cronExpression() == null || req.cronExpression().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cron 表达式不能为空");
        }
    }

    private void validateCronExpression(String cron) {
        try {
            org.springframework.scheduling.support.CronExpression.parse(cron);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无效的 cron 表达式: " + e.getMessage());
        }
    }

    private ScheduledTaskResponse toResponse(ScheduledTask task) {
        return new ScheduledTaskResponse(
                task.getId(),
                task.getName(),
                task.getDescription(),
                task.getPrompt(),
                task.getCronExpression(),
                task.getScheduleLabel(),
                task.getSkillId(),
                task.isEnabled(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }

    private ScheduledTaskLogResponse toLogResponse(ScheduledTaskLog log, String taskName) {
        return new ScheduledTaskLogResponse(
                log.getId(),
                log.getTaskId(),
                taskName,
                log.getStatus(),
                log.getResult(),
                log.getDuration(),
                log.getStartedAt(),
                log.getEndedAt()
        );
    }
}
