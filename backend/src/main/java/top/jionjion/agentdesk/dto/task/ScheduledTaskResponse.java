package top.jionjion.agentdesk.dto.task;

/**
 * 定时任务响应
 *
 * @param id             任务ID
 * @param name           任务名称
 * @param description    任务描述
 * @param prompt         执行提示词
 * @param cronExpression Cron 表达式
 * @param scheduleLabel  调度标签
 * @param skillId        关联技能ID
 * @param enabled        是否启用
 * @param createdAt      创建时间戳
 * @param updatedAt      更新时间戳
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
