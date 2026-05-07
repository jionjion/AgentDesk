package top.jionjion.agentdesk.dto.task;

/**
 * 定时任务创建/更新请求
 *
 * @author Jion
 */
public record ScheduledTaskRequest(
        String name,
        String description,
        String prompt,
        String cronExpression,
        String scheduleLabel,
        String skillId
) {
}
