package top.jionjion.agentdesk.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import top.jionjion.agentdesk.agent.core.AgentHandle;
import top.jionjion.agentdesk.agent.core.AgentPool;
import top.jionjion.agentdesk.dto.session.SessionResponse;
import top.jionjion.agentdesk.entity.Project;
import top.jionjion.agentdesk.entity.SessionMetadata;
import top.jionjion.agentdesk.repository.ChatMessageRepository;
import top.jionjion.agentdesk.repository.ProjectRepository;
import top.jionjion.agentdesk.repository.SessionRepository;
import top.jionjion.agentdesk.security.UserPrincipal;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 会话-项目绑定测试 (见开发计划 6.2)
 */
class SessionProjectBindingTest {

    private static final Long CURRENT_USER = 1L;

    private final SessionRepository sessionRepository = mock(SessionRepository.class);
    private final ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final AgentPool agentPool = mock(AgentPool.class);
    private final SessionService service = new SessionService(
            sessionRepository, chatMessageRepository, projectRepository, agentPool);

    @BeforeEach
    void loginAsCurrentUser() {
        UserPrincipal principal = new UserPrincipal(CURRENT_USER, "tester");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createWithProjectBindsAfterOwnershipCheck() {
        when(projectRepository.findByIdAndUserId("proj-1", CURRENT_USER))
                .thenReturn(Optional.of(project("proj-1")));
        when(projectRepository.findById("proj-1")).thenReturn(Optional.of(project("proj-1")));

        SessionResponse response = service.create("标题", "proj-1");

        assertEquals("proj-1", response.projectId());
        assertEquals("AgentDesk", response.projectName());
        verify(sessionRepository).save(argThat(s -> "proj-1".equals(s.getProjectId())));
    }

    @Test
    void createRejectsOtherUsersProject() {
        when(projectRepository.findByIdAndUserId("proj-x", CURRENT_USER)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.create("标题", "proj-x"));
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void createWithoutProjectLeavesProjectNull() {
        SessionResponse response = service.create("标题", null);

        assertNull(response.projectId());
        assertNull(response.projectName());
    }

    @Test
    void bindProjectUpdatesSessionAndInvalidatesAgent() {
        SessionMetadata session = session("sess-1", null);
        when(sessionRepository.findByIdAndUserId("sess-1", CURRENT_USER)).thenReturn(Optional.of(session));
        when(projectRepository.findByIdAndUserId("proj-1", CURRENT_USER))
                .thenReturn(Optional.of(project("proj-1")));
        when(agentPool.isBusy("sess-1")).thenReturn(false);

        SessionResponse response = service.bindProject("sess-1", "proj-1");

        assertEquals("proj-1", response.projectId());
        verify(sessionRepository).save(session);
        verify(agentPool).invalidate("sess-1");
    }

    @Test
    void bindProjectRefusedWhileStreaming() {
        when(sessionRepository.findByIdAndUserId("sess-1", CURRENT_USER))
                .thenReturn(Optional.of(session("sess-1", null)));
        when(agentPool.isBusy("sess-1")).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.bindProject("sess-1", "proj-1"));
        verify(sessionRepository, never()).save(any());
        verify(agentPool, never()).invalidate(any());
    }

    @Test
    void bindNullUnbindsProject() {
        SessionMetadata session = session("sess-1", "proj-1");
        when(sessionRepository.findByIdAndUserId("sess-1", CURRENT_USER)).thenReturn(Optional.of(session));
        when(agentPool.isBusy("sess-1")).thenReturn(false);

        SessionResponse response = service.bindProject("sess-1", null);

        assertNull(response.projectId());
        verify(agentPool).invalidate("sess-1");
    }

    @Test
    void bindProjectRejectsOtherUsersProject() {
        when(sessionRepository.findByIdAndUserId("sess-1", CURRENT_USER))
                .thenReturn(Optional.of(session("sess-1", null)));
        when(agentPool.isBusy("sess-1")).thenReturn(false);
        when(projectRepository.findByIdAndUserId("proj-x", CURRENT_USER)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.bindProject("sess-1", "proj-x"));
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void bindProjectReturnsNullForUnknownSession() {
        when(sessionRepository.findByIdAndUserId("sess-x", CURRENT_USER)).thenReturn(Optional.empty());

        assertNull(service.bindProject("sess-x", "proj-1"));
    }

    @Test
    void multipleSessionsCanBindSameProject() {
        SessionMetadata first = session("sess-1", null);
        SessionMetadata second = session("sess-2", null);
        when(sessionRepository.findByIdAndUserId("sess-1", CURRENT_USER)).thenReturn(Optional.of(first));
        when(sessionRepository.findByIdAndUserId("sess-2", CURRENT_USER)).thenReturn(Optional.of(second));
        when(projectRepository.findByIdAndUserId("proj-1", CURRENT_USER))
                .thenReturn(Optional.of(project("proj-1")));
        when(agentPool.isBusy(any())).thenReturn(false);

        assertEquals("proj-1", service.bindProject("sess-1", "proj-1").projectId());
        assertEquals("proj-1", service.bindProject("sess-2", "proj-1").projectId());
    }

    @Test
    void memoryModeIsPersistedAndInherited() {
        SessionMetadata session = session("sess-1", null);
        when(sessionRepository.findByIdAndUserId("sess-1", CURRENT_USER)).thenReturn(Optional.of(session));
        when(agentPool.isBusy("sess-1")).thenReturn(false);
        AgentHandle handle = mock(AgentHandle.class);
        when(agentPool.getOrCreate("sess-1")).thenReturn(handle);

        SessionResponse response = service.updateMemoryMode("sess-1", "NO_MEMORY");

        assertEquals("NO_MEMORY", response.memoryMode());
        assertEquals("NO_MEMORY", service.resolveMemoryMode("sess-1", "INHERIT"));
        verify(sessionRepository).save(session);
        // 切换记忆模式后从原始消息重建 AgentState, 清除历史轮次注入的记忆增强文本
        verify(agentPool).resetState(CURRENT_USER, "sess-1");
        verify(handle).restoreHistory(eq(CURRENT_USER), eq("sess-1"), any());
    }

    @Test
    void memoryModeUnchangedDoesNotRebuildAgentState() {
        SessionMetadata session = session("sess-1", null);
        session.setMemoryMode("NORMAL");
        when(sessionRepository.findByIdAndUserId("sess-1", CURRENT_USER)).thenReturn(Optional.of(session));
        when(agentPool.isBusy("sess-1")).thenReturn(false);

        assertEquals("NORMAL", service.updateMemoryMode("sess-1", "NORMAL").memoryMode());

        verify(agentPool, never()).resetState(any(), any());
        verify(agentPool, never()).getOrCreate(any());
    }

    @Test
    void memoryModeRejectsInvalidValues() {
        SessionMetadata session = session("sess-1", null);
        when(sessionRepository.findByIdAndUserId("sess-1", CURRENT_USER)).thenReturn(Optional.of(session));
        when(agentPool.isBusy("sess-1")).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> service.updateMemoryMode("sess-1", "BYPASS"));
    }

    private static Project project(String id) {
        Project project = new Project();
        project.setId(id);
        project.setUserId(CURRENT_USER);
        project.setName("AgentDesk");
        project.setCreatedAt(1L);
        project.setUpdatedAt(1L);
        return project;
    }

    private static SessionMetadata session(String id, String projectId) {
        SessionMetadata metadata = new SessionMetadata();
        metadata.setId(id);
        metadata.setTitle("标题");
        metadata.setCreatedAt(1L);
        metadata.setLastUsedAt(1L);
        metadata.setUserId(CURRENT_USER);
        metadata.setProjectId(projectId);
        metadata.setMemoryMode("NORMAL");
        return metadata;
    }
}
