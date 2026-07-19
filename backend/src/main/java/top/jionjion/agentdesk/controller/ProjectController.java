package top.jionjion.agentdesk.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import top.jionjion.agentdesk.dto.project.ProjectCreateRequest;
import top.jionjion.agentdesk.dto.project.ProjectResponse;
import top.jionjion.agentdesk.dto.project.ProjectUpdateRequest;
import top.jionjion.agentdesk.service.ProjectService;

import java.util.List;

/**
 * 项目管理控制器
 *
 * @author Jion
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    /**
     * 创建项目
     */
    @PostMapping
    public ProjectResponse create(@RequestBody ProjectCreateRequest request) {
        try {
            return projectService.create(request);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * 列出当前用户的所有项目
     */
    @GetMapping
    public List<ProjectResponse> listAll() {
        return projectService.listByUser();
    }

    /**
     * 获取项目详情
     */
    @GetMapping("/{id}")
    public ProjectResponse get(@PathVariable String id) {
        ProjectResponse response = projectService.get(id);
        if (response == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "项目不存在");
        }
        return response;
    }

    /**
     * 更新项目
     */
    @PutMapping("/{id}")
    public ProjectResponse update(@PathVariable String id, @RequestBody ProjectUpdateRequest request) {
        ProjectResponse response = projectService.update(id, request);
        if (response == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "项目不存在");
        }
        return response;
    }

    /**
     * 删除项目。关联会话与定时任务保留, project_id 置空。
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        if (!projectService.delete(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "项目不存在");
        }
    }
}
