package top.jionjion.agentdesk.agent.runtime;

import top.jionjion.agentdesk.dto.chat.TaskProgressDto;
import top.jionjion.agentdesk.dto.chat.TaskProgressDto.SubtaskDto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 将 Harness 内置 todo_write 工具的全量列表调用, diff 翻译为前端 task_progress 增量事件。
 * <p>
 * AgentScope v2 没有 TaskList 专用事件, 任务清单只通过 todo_write 工具(全量替换语义)维护,
 * 因此在事件桥中拦截该工具的参数并与上一次列表比较, 生成 plan_created / plan_revised /
 * task_updated / task_completed / plan_finished 事件。
 * <p>
 * todo_write 参数不携带任务 ID, 本类以任务内容为键维护稳定 UUID(镜像 TodoTools 的
 * byContent 保留逻辑), 保证同一任务跨调用 ID 不变。非线程安全, 由所属
 * {@link AgentEventBridge} 串行调用。
 *
 * @author Jion
 */
final class TaskProgressTracker {

    /** 任务内容 -> 快照(稳定 ID + 前端状态) */
    private final Map<String, Entry> previous = new LinkedHashMap<>();
    private boolean planCreated;
    private boolean planFinished;

    private record Entry(String id, String state) {
    }

    /**
     * 处理一次 todo_write 调用, 返回需要推送的 task_progress 事件列表。
     *
     * @param todos todo_write 的 todos 参数, 每项为含 content/status 的 Map
     */
    List<TaskProgressDto> onTodoWrite(List<?> todos) {
        Map<String, Entry> current = normalize(todos);
        if (current.isEmpty() && previous.isEmpty()) {
            return List.of();
        }

        List<TaskProgressDto> events = new ArrayList<>();
        boolean membershipChanged = !current.keySet().equals(previous.keySet());

        if (membershipChanged) {
            String eventType = planCreated ? "plan_revised" : "plan_created";
            planCreated = true;
            planFinished = false;
            events.add(fullListEvent(eventType, current));
        } else {
            int completed = countCompleted(current);
            int total = current.size();
            for (Map.Entry<String, Entry> e : current.entrySet()) {
                Entry now = e.getValue();
                Entry before = previous.get(e.getKey());
                if (Objects.equals(before.state(), now.state())) {
                    continue;
                }
                String eventType = "done".equals(now.state()) ? "task_completed" : "task_updated";
                events.add(new TaskProgressDto(eventType, null, null,
                        now.id(), e.getKey(), now.state(), completed, total));
            }
        }

        if (!planFinished && !current.isEmpty()
                && current.values().stream().allMatch(e -> "done".equals(e.state()))) {
            planFinished = true;
            events.add(new TaskProgressDto("plan_finished", null, null, null, null, null,
                    current.size(), current.size()));
        }

        previous.clear();
        previous.putAll(current);
        return events;
    }

    /**
     * Agent 正常完成时收尾: 模型完成最后的任务后常直接输出答案而不再调 todo_write,
     * 导致末尾任务停留在 in_progress。此处将未完成任务补为 done 并发 plan_finished。
     * 仅应在正常完成(AgentResultEvent)时调用, 中断/异常路径保持原状。
     */
    List<TaskProgressDto> onAgentComplete() {
        if (!planCreated || planFinished || previous.isEmpty()) {
            return List.of();
        }
        List<TaskProgressDto> events = new ArrayList<>();
        int total = previous.size();
        for (Map.Entry<String, Entry> e : previous.entrySet()) {
            if (!"done".equals(e.getValue().state())) {
                e.setValue(new Entry(e.getValue().id(), "done"));
                events.add(new TaskProgressDto("task_completed", null, null,
                        e.getValue().id(), e.getKey(), "done", countCompleted(previous), total));
            }
        }
        planFinished = true;
        events.add(new TaskProgressDto("plan_finished", null, null, null, null, null, total, total));
        return events;
    }

    private Map<String, Entry> normalize(List<?> todos) {
        Map<String, Entry> result = new LinkedHashMap<>();
        if (todos == null) {
            return result;
        }
        for (Object item : todos) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Object content = map.get("content");
            if (!(content instanceof String title) || title.isBlank()) {
                continue;
            }
            String state = mapState(map.get("status"));
            Entry prior = previous.get(title);
            String id = prior != null ? prior.id() : UUID.randomUUID().toString().replace("-", "");
            result.putIfAbsent(title, new Entry(id, state));
        }
        return result;
    }

    /** pending/in_progress/completed -> 前端契约 todo/in_progress/done */
    private static String mapState(Object status) {
        String value = status instanceof String s ? s.trim().toLowerCase() : "";
        return switch (value) {
            case "completed" -> "done";
            case "in_progress" -> "in_progress";
            default -> "todo";
        };
    }

    private TaskProgressDto fullListEvent(String eventType, Map<String, Entry> current) {
        List<SubtaskDto> subtasks = current.entrySet().stream()
                .map(e -> new SubtaskDto(e.getValue().id(), e.getKey(), e.getValue().state()))
                .toList();
        return new TaskProgressDto(eventType, null, subtasks, null, null, null,
                countCompleted(current), current.size());
    }

    private static int countCompleted(Map<String, Entry> current) {
        return (int) current.values().stream().filter(e -> "done".equals(e.state())).count();
    }
}
