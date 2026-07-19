package top.jionjion.agentdesk.dto.task;

/**
 * 定时任务创建/更新请求
 *
 * @param name           任务名称
 * @param description    任务描述
 * @param prompt         执行提示词
 * @param cronExpression Cron 表达式
 * @param scheduleLabel  调度标签(前端展示用)
 * @param skillId        关联技能ID
 * @param projectId      可选关联项目ID
 * @param deviceId       目标设备ID, 与 projectId 必须同时提供
 * @author Jion
 */
public record ScheduledTaskRequest(
        String name,
        String description,
        String prompt,
        String cronExpression,
        String scheduleLabel,
        String skillId,
        String projectId,
        String deviceId
) {
}
