package top.jionjion.agentdesk.scheduler;

import io.agentscope.core.message.Msg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import top.jionjion.agentdesk.agent.AgentFactory;
import top.jionjion.agentdesk.agent.AgentHandle;
import top.jionjion.agentdesk.entity.ScheduledTask;
import top.jionjion.agentdesk.entity.ScheduledTaskLog;
import top.jionjion.agentdesk.entity.User;
import top.jionjion.agentdesk.repository.ScheduledTaskLogRepository;
import top.jionjion.agentdesk.repository.ScheduledTaskRepository;
import top.jionjion.agentdesk.repository.UserRepository;
import top.jionjion.agentdesk.security.UserPrincipal;

import java.util.Collections;

/**
 * 定时任务执行器: 在调度线程中执行 Agent 对话
 *
 * @author Jion
 */
@Component
public class ScheduledTaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskExecutor.class);

    private final ScheduledTaskRepository taskRepository;
    private final ScheduledTaskLogRepository logRepository;
    private final UserRepository userRepository;
    private final AgentFactory agentFactory;

    public ScheduledTaskExecutor(ScheduledTaskRepository taskRepository,
                                 ScheduledTaskLogRepository logRepository,
                                 UserRepository userRepository,
                                 AgentFactory agentFactory) {
        this.taskRepository = taskRepository;
        this.logRepository = logRepository;
        this.userRepository = userRepository;
        this.agentFactory = agentFactory;
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

            // 创建一次性 Agent
            String sessionId = "sched-" + taskId + "-" + startTime;
            AgentHandle handle = agentFactory.createAgent(sessionId);

            // 构建消息并执行
            Msg userMsg = Msg.builder()
                    .textContent(task.getPrompt())
                    .build();

            // 阻塞执行 Agent 流
            handle.agent().stream(userMsg).blockLast();

            // 获取回复
            String reply = handle.hook().getLastReply();

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
