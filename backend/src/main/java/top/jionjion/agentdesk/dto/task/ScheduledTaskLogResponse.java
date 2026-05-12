package top.jionjion.agentdesk.dto.task;

/**
 * 定时任务执行记录响应
 *
 * @param id        记录ID
 * @param taskId    任务ID
 * @param taskName  任务名称
 * @param status    执行状态
 * @param result    执行结果
 * @param duration  执行耗时(毫秒)
 * @param startedAt 开始时间戳
 * @param endedAt   结束时间戳
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
