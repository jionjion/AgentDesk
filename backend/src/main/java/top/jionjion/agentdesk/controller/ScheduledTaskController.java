package top.jionjion.agentdesk.controller;

import org.springframework.web.bind.annotation.*;
import top.jionjion.agentdesk.dto.ScheduledTaskEnabledRequest;
import top.jionjion.agentdesk.dto.ScheduledTaskLogResponse;
import top.jionjion.agentdesk.dto.ScheduledTaskRequest;
import top.jionjion.agentdesk.dto.ScheduledTaskResponse;
import top.jionjion.agentdesk.security.UserContext;
import top.jionjion.agentdesk.service.ScheduledTaskService;

import java.util.List;
import java.util.Map;

/**
 * 定时任务控制器
 *
 * @author Jion
 */
@RestController
@RequestMapping("/api/scheduled-tasks")
public class ScheduledTaskController {

    private final ScheduledTaskService scheduledTaskService;

    public ScheduledTaskController(ScheduledTaskService scheduledTaskService) {
        this.scheduledTaskService = scheduledTaskService;
    }

    /**
     * 列出用户的所有定时任务
     */
    @GetMapping
    public List<ScheduledTaskResponse> list() {
        return scheduledTaskService.listTasks(UserContext.getUserId());
    }

    /**
     * 获取单个定时任务详情
     */
    @GetMapping("/{id}")
    public ScheduledTaskResponse get(@PathVariable Long id) {
        return scheduledTaskService.getTask(id, UserContext.getUserId());
    }

    /**
     * 创建定时任务
     */
    @PostMapping
    public ScheduledTaskResponse create(@RequestBody ScheduledTaskRequest request) {
        return scheduledTaskService.createTask(request, UserContext.getUserId());
    }

    /**
     * 更新定时任务
     */
    @PutMapping("/{id}")
    public ScheduledTaskResponse update(@PathVariable Long id, @RequestBody ScheduledTaskRequest request) {
        return scheduledTaskService.updateTask(id, request, UserContext.getUserId());
    }

    /**
     * 删除定时任务
     */
    @DeleteMapping("/{id}")
    public Map<String, String> delete(@PathVariable Long id) {
        scheduledTaskService.deleteTask(id, UserContext.getUserId());
        return Map.of("message", "定时任务已删除");
    }

    /**
     * 启用/禁用定时任务
     */
    @PutMapping("/{id}/enabled")
    public Map<String, String> setEnabled(@PathVariable Long id, @RequestBody ScheduledTaskEnabledRequest request) {
        scheduledTaskService.setEnabled(id, request.enabled(), UserContext.getUserId());
        return Map.of("message", request.enabled() ? "定时任务已启用" : "定时任务已禁用");
    }

    /**
     * 手动立即执行
     */
    @PostMapping("/{id}/run")
    public Map<String, String> runNow(@PathVariable Long id) {
        scheduledTaskService.runTaskNow(id, UserContext.getUserId());
        return Map.of("message", "任务已提交执行");
    }

    /**
     * 获取用户全部执行记录
     */
    @GetMapping("/logs")
    public List<ScheduledTaskLogResponse> getLogs() {
        return scheduledTaskService.getLogs(UserContext.getUserId());
    }

    /**
     * 获取单个任务的执行记录
     */
    @GetMapping("/{id}/logs")
    public List<ScheduledTaskLogResponse> getTaskLogs(@PathVariable Long id) {
        return scheduledTaskService.getLogsByTaskId(id, UserContext.getUserId());
    }
}
