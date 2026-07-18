package top.jionjion.agentdesk.dto.chat;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * SSE 任务进度事件载荷，用于追踪 Harness Task List 的执行进度
 *
 * @param eventType      事件子类型: plan_created / task_updated / task_completed / plan_revised / plan_finished
 * @param planTitle      计划标题
 * @param subtasks       子任务列表（create/revise 时发送完整列表）
 * @param subtaskId      被更新的子任务 ID
 * @param subtaskTitle   被更新的子任务标题
 * @param newState       新状态: todo / in_progress / done / abandoned
 * @param completedCount 已完成子任务数
 * @param totalCount     子任务总数
 * @author Jion
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskProgressDto(
        String eventType,
        String planTitle,
        List<SubtaskDto> subtasks,
        String subtaskId,
        String subtaskTitle,
        String newState,
        Integer completedCount,
        Integer totalCount
) {

    /**
     * 子任务信息
     *
     * @param id    子任务 ID
     * @param title 子任务标题
     * @param state 状态: todo / in_progress / done / abandoned
     */
    public record SubtaskDto(String id, String title, String state) {
    }
}
