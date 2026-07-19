package top.jionjion.agentdesk.service.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import top.jionjion.agentdesk.agent.runtime.ProjectRuntimeContext;
import top.jionjion.agentdesk.dto.chat.ChatRequest;
import top.jionjion.agentdesk.entity.Project;
import top.jionjion.agentdesk.repository.ProjectRepository;
import top.jionjion.agentdesk.repository.SessionRepository;

/**
 * 调用级 ProjectRuntimeContext 解析器。
 *
 * <p>信任模型 (见开发计划 5.3/9.2):
 * <ul>
 *   <li>项目归属以服务器数据库 Session->Project 关系为准, 不信任请求体中的 projectId</li>
 *   <li>rootPath/解释器信息来自在线客户端本轮提供的 runtime snapshot</li>
 *   <li>snapshot 的 projectId 与会话绑定项目不一致时按"运行环境离线"处理, 不静默采用</li>
 * </ul>
 *
 * @author Jion
 */
@Service
public class ProjectRuntimeContextResolver {

    private static final Logger log = LoggerFactory.getLogger(ProjectRuntimeContextResolver.class);

    private final SessionRepository sessionRepository;
    private final ProjectRepository projectRepository;

    public ProjectRuntimeContextResolver(SessionRepository sessionRepository,
                                         ProjectRepository projectRepository) {
        this.sessionRepository = sessionRepository;
        this.projectRepository = projectRepository;
    }

    /**
     * 解析聊天调用的项目上下文。
     *
     * @param userId   当前用户
     * @param sessionId 会话ID
     * @param snapshot 客户端本轮上报的 runtime snapshot (可空)
     * @return 项目上下文; 会话未绑定项目时返回 null
     */
    public ProjectRuntimeContext resolve(Long userId, String sessionId,
                                         ChatRequest.RuntimeSnapshot snapshot) {
        String projectId = sessionRepository.findByIdAndUserId(sessionId, userId)
                .map(s -> s.getProjectId())
                .orElse(null);
        if (projectId == null) {
            return null;
        }
        Project project = projectRepository.findByIdAndUserId(projectId, userId).orElse(null);
        if (project == null) {
            // 绑定的项目已被删除 (外键置空前的竞态), 按未绑定处理
            log.warn("会话 {} 绑定的项目 {} 不存在, 按无项目处理", sessionId, projectId);
            return null;
        }
        return buildContext(project, snapshot);
    }

    /**
     * 由已验证归属的 Project 与 snapshot 构造上下文 (供聊天与定时任务共用)。
     * snapshot 为 null 或 projectId 不匹配时按运行环境离线处理。
     */
    public ProjectRuntimeContext buildContext(Project project, ChatRequest.RuntimeSnapshot snapshot) {
        boolean snapshotValid = snapshot != null
                && project.getId().equals(snapshot.projectId())
                && snapshot.rootPath() != null && !snapshot.rootPath().isBlank();
        if (snapshot != null && !snapshotValid) {
            log.warn("runtime snapshot 无效或项目不匹配: 期望项目 {}, snapshot 项目 {}",
                    project.getId(), snapshot.projectId());
        }
        if (!snapshotValid) {
            return new ProjectRuntimeContext(
                    project.getId(), project.getName(), project.getInstructions(),
                    null, null, null, null, null, null, false);
        }
        return new ProjectRuntimeContext(
                project.getId(),
                project.getName(),
                project.getInstructions(),
                snapshot.deviceId(),
                snapshot.rootPath(),
                snapshot.cwd(),
                snapshot.platform(),
                snapshot.pythonExecutable(),
                snapshot.pythonVersion(),
                true);
    }
}
