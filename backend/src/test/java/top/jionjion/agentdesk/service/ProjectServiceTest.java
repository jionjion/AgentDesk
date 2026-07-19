package top.jionjion.agentdesk.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import top.jionjion.agentdesk.dto.project.ProjectCreateRequest;
import top.jionjion.agentdesk.dto.project.ProjectResponse;
import top.jionjion.agentdesk.dto.project.ProjectUpdateRequest;
import top.jionjion.agentdesk.entity.Project;
import top.jionjion.agentdesk.repository.ProjectRepository;
import top.jionjion.agentdesk.security.UserPrincipal;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 项目服务测试: 归属校验、创建与删除语义
 */
class ProjectServiceTest {

    private static final Long CURRENT_USER = 1L;
    private static final Long OTHER_USER = 2L;

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final ProjectService service = new ProjectService(projectRepository);

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
    void createPersistsProjectForCurrentUser() {
        ProjectResponse response = service.create(new ProjectCreateRequest("AgentDesk", "说明", "指令"));

        assertNotNull(response.id());
        assertEquals(16, response.id().length());
        assertEquals("AgentDesk", response.name());
        verify(projectRepository).save(argThat(p -> CURRENT_USER.equals(p.getUserId())));
    }

    @Test
    void createRejectsBlankName() {
        assertThrows(IllegalArgumentException.class,
                () -> service.create(new ProjectCreateRequest("  ", null, null)));
        verify(projectRepository, never()).save(any());
    }

    @Test
    void getReturnsNullForOtherUsersProject() {
        // findByIdAndUserId 按当前用户过滤, 其他用户的项目查询结果为空
        when(projectRepository.findByIdAndUserId("proj-1", CURRENT_USER)).thenReturn(Optional.empty());

        assertNull(service.get("proj-1"));
    }

    @Test
    void updateReturnsNullWhenNotOwned() {
        when(projectRepository.findByIdAndUserId("proj-1", CURRENT_USER)).thenReturn(Optional.empty());

        assertNull(service.update("proj-1", new ProjectUpdateRequest("新名", null, null)));
        verify(projectRepository, never()).save(any());
    }

    @Test
    void deleteReturnsFalseWhenNotOwned() {
        when(projectRepository.findByIdAndUserId("proj-1", CURRENT_USER)).thenReturn(Optional.empty());

        assertFalse(service.delete("proj-1"));
        verify(projectRepository, never()).delete(any());
    }

    @Test
    void deleteRemovesOwnedProject() {
        Project project = ownedProject("proj-1");
        when(projectRepository.findByIdAndUserId("proj-1", CURRENT_USER)).thenReturn(Optional.of(project));

        assertTrue(service.delete("proj-1"));
        verify(projectRepository).delete(project);
    }

    private static Project ownedProject(String id) {
        Project project = new Project();
        project.setId(id);
        project.setUserId(CURRENT_USER);
        project.setName("AgentDesk");
        project.setCreatedAt(1L);
        project.setUpdatedAt(1L);
        return project;
    }
}
