package top.jionjion.agentdesk.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import top.jionjion.agentdesk.agent.core.AgentFactory;
import top.jionjion.agentdesk.agent.core.AgentHandle;
import top.jionjion.agentdesk.agent.runtime.AgentInput;
import top.jionjion.agentdesk.agent.runtime.AgentRunContext;
import top.jionjion.agentdesk.agent.runtime.ProjectRuntimeContext;
import top.jionjion.agentdesk.dto.chat.ChatRequest;
import top.jionjion.agentdesk.entity.Project;
import top.jionjion.agentdesk.entity.ScheduledTask;
import top.jionjion.agentdesk.entity.ScheduledTaskLog;
import top.jionjion.agentdesk.entity.User;
import top.jionjion.agentdesk.repository.ProjectRepository;
import top.jionjion.agentdesk.repository.ScheduledTaskLogRepository;
import top.jionjion.agentdesk.repository.ScheduledTaskRepository;
import top.jionjion.agentdesk.repository.UserRepository;
import top.jionjion.agentdesk.security.UserPrincipal;
import top.jionjion.agentdesk.service.chat.ProjectRuntimeContextResolver;
import top.jionjion.agentdesk.websocket.RemoteExecBridge;

import java.util.Collections;
import java.util.Map;

/**
 * 定时任务执行器: 在调度线程中执行 Agent 对话。
 * <p>
 * 项目型任务 (projectId 非空) 在执行前通过 {@code runtime_snapshot_request}
 * 向任务保存的目标设备获取 runtime snapshot, 构造与聊天一致的
 * {@link ProjectRuntimeContext} (见开发计划 8.4)。
 *
 * @author Jion
 */
@Component
public class ScheduledTaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskExecutor.class);

    private final ScheduledTaskRepository taskRepository;
    private final ScheduledTaskLogRepository logRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final AgentFactory agentFactory;
    private final RemoteExecBridge remoteExecBridge;
    private final ProjectRuntimeContextResolver projectContextResolver;
    private final long snapshotTimeoutMs;

    public ScheduledTaskExecutor(ScheduledTaskRepository taskRepository,
                                 ScheduledTaskLogRepository logRepository,
                                 UserRepository userRepository,
                                 ProjectRepository projectRepository,
                                 AgentFactory agentFactory,
                                 RemoteExecBridge remoteExecBridge,
                                 ProjectRuntimeContextResolver projectContextResolver,
                                 @Value("${agentdesk.scheduler.snapshot-timeout:10000}") long snapshotTimeoutMs) {
        this.taskRepository = taskRepository;
        this.logRepository = logRepository;
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.agentFactory = agentFactory;
        this.remoteExecBridge = remoteExecBridge;
        this.projectContextResolver = projectContextResolver;
        this.snapshotTimeoutMs = snapshotTimeoutMs;
    }

    /**
     * 执行一次定时任务
     */
    public void execute(Long taskId, Long userId) {
        long startTime = System.currentTimeMillis();
        ScheduledTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            log.warn("定时任务不存在: id={}", taskId);
            return;
        }

        // 创建执行记录
        ScheduledTaskLog taskLog = new ScheduledTaskLog();
        taskLog.setTaskId(taskId);
        taskLog.setUserId(userId);
        taskLog.setStatus("RUNNING");
        taskLog.setStartedAt(startTime);
        logRepository.save(taskLog);

        try {
            // 设置 SecurityContext, 使 AgentFactory 能读取用户配置
            setupSecurityContext(userId);

            // 项目型任务: 先向目标设备获取 runtime snapshot (失败抛出稳定错误码)
            ProjectRuntimeContext projectContext = resolveProjectContext(task, userId);

            // 创建一次性 Agent
            String sessionId = "sched-" + taskId + "-" + startTime;
            String reply;
            try (AgentHandle handle = agentFactory.createAgent(sessionId)) {
                // 阻塞执行 Agent 流
                AgentRunContext runContext = AgentRunContext.of(userId, sessionId, projectContext);
                handle.stream(AgentInput.text(task.getPrompt()), runContext).blockLast();
                reply = handle.lastReply();
            }

            long endTime = System.currentTimeMillis();
            taskLog.setStatus("SUCCESS");
            taskLog.setResult(reply);
            taskLog.setDuration(endTime - startTime);
            taskLog.setEndedAt(endTime);
            logRepository.save(taskLog);

            log.info("定时任务执行成功: id={}, name={}, duration={}ms", taskId, task.getName(), endTime - startTime);
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            taskLog.setStatus("FAILED");
            taskLog.setResult(e.getMessage());
            taskLog.setDuration(endTime - startTime);
            taskLog.setEndedAt(endTime);
            logRepository.save(taskLog);

            log.error("定时任务执行失败: id={}, name={}, error={}", taskId, task.getName(), e.getMessage(), e);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * 解析项目型任务的运行时上下文。
     * <p>
     * 未绑定项目返回 null (按服务器侧普通任务运行);
     * 已绑定项目时校验归属并向目标设备请求 snapshot, 失败抛出携带稳定错误码的异常。
     */
    private ProjectRuntimeContext resolveProjectContext(ScheduledTask task, Long userId) throws Exception {
        String projectId = task.getProjectId();
        if (projectId == null || projectId.isBlank()) {
            return null;
        }
        Project project = projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new IllegalStateException(
                        "PROJECT_NOT_FOUND: 任务绑定的项目 " + projectId + " 不存在或不属于当前用户"));

        Map<String, Object> snapshot = remoteExecBridge.requestRuntimeSnapshot(
                userId, projectId, task.getDeviceId(), task.getId(), snapshotTimeoutMs);

        ChatRequest.RuntimeSnapshot runtimeSnapshot = new ChatRequest.RuntimeSnapshot(
                (String) snapshot.get("projectId"),
                (String) snapshot.get("deviceId"),
                (String) snapshot.get("rootPath"),
                (String) snapshot.get("cwd"),
                (String) snapshot.get("platform"),
                (String) snapshot.get("pythonExecutable"),
                (String) snapshot.get("pythonVersion"));
        return projectContextResolver.buildContext(project, runtimeSnapshot);
    }

    /**
     * 为调度线程设置 SecurityContext
     */
    private void setupSecurityContext(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("用户不存在: " + userId));
        UserPrincipal principal = new UserPrincipal(user.getId(), user.getUsername());
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
