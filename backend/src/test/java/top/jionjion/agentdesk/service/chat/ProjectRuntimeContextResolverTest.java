package top.jionjion.agentdesk.service.chat;

import org.junit.jupiter.api.Test;
import top.jionjion.agentdesk.agent.runtime.ProjectRuntimeContext;
import top.jionjion.agentdesk.dto.chat.ChatRequest;
import top.jionjion.agentdesk.entity.Project;
import top.jionjion.agentdesk.entity.SessionMetadata;
import top.jionjion.agentdesk.repository.ProjectRepository;
import top.jionjion.agentdesk.repository.SessionRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ProjectRuntimeContext 解析测试: 信任模型与 snapshot 校验 (见开发计划 5.3)
 */
class ProjectRuntimeContextResolverTest {

    private static final Long USER = 1L;

    private final SessionRepository sessionRepository = mock(SessionRepository.class);
    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final ProjectRuntimeContextResolver resolver =
            new ProjectRuntimeContextResolver(sessionRepository, projectRepository);

    @Test
    void unboundSessionYieldsNullContext() {
        when(sessionRepository.findByIdAndUserId("sess-1", USER))
                .thenReturn(Optional.of(session("sess-1", null)));

        assertNull(resolver.resolve(USER, "sess-1", snapshot("proj-1")));
    }

    @Test
    void boundSessionWithValidSnapshotIsOnline() {
        when(sessionRepository.findByIdAndUserId("sess-1", USER))
                .thenReturn(Optional.of(session("sess-1", "proj-1")));
        when(projectRepository.findByIdAndUserId("proj-1", USER))
                .thenReturn(Optional.of(project("proj-1")));

        ProjectRuntimeContext ctx = resolver.resolve(USER, "sess-1", snapshot("proj-1"));

        assertNotNull(ctx);
        assertTrue(ctx.runtimeOnline());
        assertEquals("proj-1", ctx.projectId());
        assertEquals("W:\\Demo", ctx.rootPath());
        assertEquals("W:\\Demo", ctx.effectiveCwd());
        assertEquals("3.12.4", ctx.pythonVersion());
    }

    @Test
    void snapshotForDifferentProjectIsRejectedNotSilentlyAdopted() {
        // snapshot 的 projectId 与会话绑定项目不一致: 按离线处理, 不采用 snapshot 路径
        when(sessionRepository.findByIdAndUserId("sess-1", USER))
                .thenReturn(Optional.of(session("sess-1", "proj-1")));
        when(projectRepository.findByIdAndUserId("proj-1", USER))
                .thenReturn(Optional.of(project("proj-1")));

        ProjectRuntimeContext ctx = resolver.resolve(USER, "sess-1", snapshot("proj-other"));

        assertNotNull(ctx);
        assertFalse(ctx.runtimeOnline());
        assertNull(ctx.rootPath());
        assertEquals("proj-1", ctx.projectId());
    }

    @Test
    void missingSnapshotYieldsOfflineContextWithProjectIdentity() {
        when(sessionRepository.findByIdAndUserId("sess-1", USER))
                .thenReturn(Optional.of(session("sess-1", "proj-1")));
        when(projectRepository.findByIdAndUserId("proj-1", USER))
                .thenReturn(Optional.of(project("proj-1")));

        ProjectRuntimeContext ctx = resolver.resolve(USER, "sess-1", null);

        assertNotNull(ctx);
        assertFalse(ctx.runtimeOnline());
        assertEquals("proj-1", ctx.projectId());
        assertEquals("Demo", ctx.projectName());
    }

    @Test
    void deletedProjectIsTreatedAsUnbound() {
        when(sessionRepository.findByIdAndUserId("sess-1", USER))
                .thenReturn(Optional.of(session("sess-1", "proj-gone")));
        when(projectRepository.findByIdAndUserId("proj-gone", USER)).thenReturn(Optional.empty());

        assertNull(resolver.resolve(USER, "sess-1", snapshot("proj-gone")));
    }

    @Test
    void cwdFallsBackToRootPath() {
        ChatRequest.RuntimeSnapshot withCwd = new ChatRequest.RuntimeSnapshot(
                "proj-1", "dev-1", "W:\\Demo", "W:\\Demo\\sub", "win32", null, null);
        ProjectRuntimeContext ctx = resolver.buildContext(project("proj-1"), withCwd);
        assertEquals("W:\\Demo\\sub", ctx.effectiveCwd());

        ChatRequest.RuntimeSnapshot withoutCwd = new ChatRequest.RuntimeSnapshot(
                "proj-1", "dev-1", "W:\\Demo", null, "win32", null, null);
        assertEquals("W:\\Demo", resolver.buildContext(project("proj-1"), withoutCwd).effectiveCwd());
    }

    private static ChatRequest.RuntimeSnapshot snapshot(String projectId) {
        return new ChatRequest.RuntimeSnapshot(
                projectId, "dev-1", "W:\\Demo", null, "win32",
                "C:\\Python312\\python.exe", "3.12.4");
    }

    private static Project project(String id) {
        Project project = new Project();
        project.setId(id);
        project.setUserId(USER);
        project.setName("Demo");
        project.setCreatedAt(1L);
        project.setUpdatedAt(1L);
        return project;
    }

    private static SessionMetadata session(String id, String projectId) {
        SessionMetadata metadata = new SessionMetadata();
        metadata.setId(id);
        metadata.setTitle("t");
        metadata.setCreatedAt(1L);
        metadata.setLastUsedAt(1L);
        metadata.setUserId(USER);
        metadata.setProjectId(projectId);
        return metadata;
    }
}
