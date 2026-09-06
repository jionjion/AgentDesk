package top.jionjion.agentdesk.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import top.jionjion.agentdesk.dto.project.ProjectCreateRequest;
import top.jionjion.agentdesk.dto.project.ProjectResponse;
import top.jionjion.agentdesk.dto.project.ProjectUpdateRequest;
import top.jionjion.agentdesk.entity.Project;
import top.jionjion.agentdesk.repository.ProjectRepository;
import top.jionjion.agentdesk.security.UserContext;

import java.util.List;
import java.util.UUID;

/**
 * 项目管理服务。所有查询按当前用户做归属校验。
 *
 * <p>删除项目不删除会话与定时任务, 由数据库外键 ON DELETE SET NULL 置空关联。
 *
 * @author Jion
 */
@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final MemoryService memoryService;

    @Autowired
    public ProjectService(ProjectRepository projectRepository, MemoryService memoryService) {
        this.projectRepository = projectRepository;
        this.memoryService = memoryService;
    }

    /** Test/backward-compatible constructor. */
    public ProjectService(ProjectRepository projectRepository) {
        this(projectRepository, null);
    }

    /**
     * 创建项目
     */
    public ProjectResponse create(ProjectCreateRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("项目名称不能为空");
        }
        Long userId = UserContext.getUserId();
        long now = System.currentTimeMillis();

        Project project = new Project();
        project.setId(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        project.setUserId(userId);
        project.setName(request.name().trim());
        project.setDescription(request.description());
        project.setInstructions(request.instructions());
        project.setCreatedAt(now);
        project.setUpdatedAt(now);
        projectRepository.save(project);

        return toResponse(project);
    }

    /**
     * 列出当前用户的所有项目 (按更新时间倒序)
     */
    public List<ProjectResponse> listByUser() {
        Long userId = UserContext.getUserId();
        return projectRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 获取当前用户的项目详情
     */
    public ProjectResponse get(String id) {
        Long userId = UserContext.getUserId();
        return projectRepository.findByIdAndUserId(id, userId).map(this::toResponse).orElse(null);
    }

    /**
     * 更新当前用户的项目
     */
    public ProjectResponse update(String id, ProjectUpdateRequest request) {
        Long userId = UserContext.getUserId();
        return projectRepository.findByIdAndUserId(id, userId).map(p -> {
            if (request.name() != null && !request.name().isBlank()) {
                p.setName(request.name().trim());
            }
            p.setDescription(request.description());
            p.setInstructions(request.instructions());
            p.setUpdatedAt(System.currentTimeMillis());
            projectRepository.save(p);
            return toResponse(p);
        }).orElse(null);
    }

    /**
     * 删除当前用户的项目。会话与定时任务保留, project_id 由数据库外键置空。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(String id) {
        Long userId = UserContext.getUserId();
        return projectRepository.findByIdAndUserId(id, userId).map(p -> {
            if (memoryService != null) memoryService.deleteProjectScope(userId, id);
            projectRepository.delete(p);
            return true;
        }).orElse(false);
    }

    /**
     * 校验项目归属当前用户
     */
    public boolean belongsToUser(String projectId) {
        Long userId = UserContext.getUserId();
        return projectRepository.findByIdAndUserId(projectId, userId).isPresent();
    }

    private ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getInstructions(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }
}
