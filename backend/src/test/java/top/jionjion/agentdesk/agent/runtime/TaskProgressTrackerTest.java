package top.jionjion.agentdesk.agent.runtime;

import org.junit.jupiter.api.Test;
import top.jionjion.agentdesk.dto.chat.TaskProgressDto;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TaskProgressTracker 的 todo_write diff 逻辑测试
 *
 * @author Jion
 */
class TaskProgressTrackerTest {

    private static Map<String, Object> todo(String content, String status) {
        return Map.of("content", content, "status", status);
    }

    @Test
    void 首次列表应产生planCreated并携带完整子任务() {
        TaskProgressTracker tracker = new TaskProgressTracker();
        List<TaskProgressDto> events = tracker.onTodoWrite(List.of(
                todo("步骤一", "in_progress"),
                todo("步骤二", "pending")));

        assertEquals(1, events.size());
        TaskProgressDto dto = events.getFirst();
        assertEquals("plan_created", dto.eventType());
        assertEquals(2, dto.subtasks().size());
        assertEquals("步骤一", dto.subtasks().getFirst().title());
        assertEquals("in_progress", dto.subtasks().getFirst().state());
        assertEquals("todo", dto.subtasks().get(1).state());
        assertEquals(0, dto.completedCount());
        assertEquals(2, dto.totalCount());
        assertNotNull(dto.subtasks().getFirst().id());
    }

    @Test
    void 状态推进应产生taskUpdated且ID跨调用稳定() {
        TaskProgressTracker tracker = new TaskProgressTracker();
        List<TaskProgressDto> created = tracker.onTodoWrite(List.of(
                todo("步骤一", "pending"),
                todo("步骤二", "pending")));
        String firstId = created.getFirst().subtasks().getFirst().id();

        List<TaskProgressDto> events = tracker.onTodoWrite(List.of(
                todo("步骤一", "in_progress"),
                todo("步骤二", "pending")));

        assertEquals(1, events.size());
        TaskProgressDto dto = events.getFirst();
        assertEquals("task_updated", dto.eventType());
        assertEquals(firstId, dto.subtaskId());
        assertEquals("步骤一", dto.subtaskTitle());
        assertEquals("in_progress", dto.newState());
    }

    @Test
    void 任务完成应产生taskCompleted() {
        TaskProgressTracker tracker = new TaskProgressTracker();
        tracker.onTodoWrite(List.of(todo("步骤一", "in_progress"), todo("步骤二", "pending")));

        List<TaskProgressDto> events = tracker.onTodoWrite(List.of(
                todo("步骤一", "completed"),
                todo("步骤二", "in_progress")));

        assertEquals(2, events.size());
        assertTrue(events.stream().anyMatch(e ->
                "task_completed".equals(e.eventType()) && "步骤一".equals(e.subtaskTitle())
                        && "done".equals(e.newState())));
        assertTrue(events.stream().anyMatch(e ->
                "task_updated".equals(e.eventType()) && "步骤二".equals(e.subtaskTitle())));
        assertEquals(1, events.getFirst().completedCount());
    }

    @Test
    void 条目增删应产生planRevised() {
        TaskProgressTracker tracker = new TaskProgressTracker();
        List<TaskProgressDto> created = tracker.onTodoWrite(List.of(
                todo("步骤一", "completed"), todo("步骤二", "pending")));
        String firstId = created.getFirst().subtasks().getFirst().id();

        List<TaskProgressDto> events = tracker.onTodoWrite(List.of(
                todo("步骤一", "completed"),
                todo("步骤三", "pending")));

        assertEquals(1, events.size());
        TaskProgressDto dto = events.getFirst();
        assertEquals("plan_revised", dto.eventType());
        assertEquals(2, dto.subtasks().size());
        // 保留条目的 ID 不变
        assertEquals(firstId, dto.subtasks().getFirst().id());
    }

    @Test
    void 全部完成应追加planFinished且不重复() {
        TaskProgressTracker tracker = new TaskProgressTracker();
        tracker.onTodoWrite(List.of(todo("步骤一", "in_progress")));

        List<TaskProgressDto> events = tracker.onTodoWrite(List.of(todo("步骤一", "completed")));
        assertEquals(2, events.size());
        assertEquals("task_completed", events.getFirst().eventType());
        assertEquals("plan_finished", events.get(1).eventType());

        // 重复提交同一列表不再产生事件
        assertTrue(tracker.onTodoWrite(List.of(todo("步骤一", "completed"))).isEmpty());
    }

    @Test
    void 修订后可再次完成() {
        TaskProgressTracker tracker = new TaskProgressTracker();
        tracker.onTodoWrite(List.of(todo("步骤一", "completed")));

        // 首次即全完成: plan_created + plan_finished
        // 修订新增任务后 planFinished 重置
        List<TaskProgressDto> revised = tracker.onTodoWrite(List.of(
                todo("步骤一", "completed"), todo("步骤二", "pending")));
        assertEquals(1, revised.size());
        assertEquals("plan_revised", revised.getFirst().eventType());

        List<TaskProgressDto> finished = tracker.onTodoWrite(List.of(
                todo("步骤一", "completed"), todo("步骤二", "completed")));
        assertTrue(finished.stream().anyMatch(e -> "plan_finished".equals(e.eventType())));
    }

    @Test
    void 空或非法输入不产生事件() {
        TaskProgressTracker tracker = new TaskProgressTracker();
        assertTrue(tracker.onTodoWrite(null).isEmpty());
        assertTrue(tracker.onTodoWrite(List.of()).isEmpty());
        assertTrue(tracker.onTodoWrite(List.of("not-a-map", Map.of("status", "pending"))).isEmpty());
    }

    @Test
    void 首次全完成列表应同时产生planCreated与planFinished() {
        TaskProgressTracker tracker = new TaskProgressTracker();
        List<TaskProgressDto> events = tracker.onTodoWrite(List.of(todo("步骤一", "completed")));
        assertEquals(2, events.size());
        assertEquals("plan_created", events.getFirst().eventType());
        assertEquals("plan_finished", events.get(1).eventType());
    }

    @Test
    void agent完成时未完结任务应补发taskCompleted与planFinished() {
        TaskProgressTracker tracker = new TaskProgressTracker();
        List<TaskProgressDto> created = tracker.onTodoWrite(List.of(
                todo("步骤一", "completed"),
                todo("步骤二", "in_progress"),
                todo("步骤三", "pending")));
        String secondId = created.getFirst().subtasks().get(1).id();

        List<TaskProgressDto> events = tracker.onAgentComplete();

        // 步骤二/步骤三补为 done + plan_finished
        assertEquals(3, events.size());
        assertEquals("task_completed", events.getFirst().eventType());
        assertEquals(secondId, events.getFirst().subtaskId());
        assertEquals("done", events.getFirst().newState());
        assertEquals("task_completed", events.get(1).eventType());
        assertEquals("步骤三", events.get(1).subtaskTitle());
        TaskProgressDto finished = events.get(2);
        assertEquals("plan_finished", finished.eventType());
        assertEquals(3, finished.completedCount());
        assertEquals(3, finished.totalCount());

        // 重复调用不再产生事件
        assertTrue(tracker.onAgentComplete().isEmpty());
    }

    @Test
    void agent完成时若计划已完结或无计划则不补发() {
        TaskProgressTracker noPlan = new TaskProgressTracker();
        assertTrue(noPlan.onAgentComplete().isEmpty());

        TaskProgressTracker finished = new TaskProgressTracker();
        finished.onTodoWrite(List.of(todo("步骤一", "completed")));
        assertTrue(finished.onAgentComplete().isEmpty());
    }
}
