package top.jionjion.agentdesk.dto;

/**
 * 定时任务执行记录响应
 *
 * @author Jion
 */
public record ScheduledTaskLogResponse(
        Long id,
        Long taskId,
        String taskName,
        String status,
        String result,
        long duration,
        long startedAt,
        Long endedAt
) {
}
