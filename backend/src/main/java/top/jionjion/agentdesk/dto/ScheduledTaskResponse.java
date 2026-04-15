package top.jionjion.agentdesk.dto;

/**
 * 定时任务响应
 *
 * @author Jion
 */
public record ScheduledTaskResponse(
        Long id,
        String name,
        String description,
        String prompt,
        String cronExpression,
        String scheduleLabel,
        String skillId,
        boolean enabled,
        long createdAt,
        long updatedAt
) {
}
