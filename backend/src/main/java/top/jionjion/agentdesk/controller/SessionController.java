package top.jionjion.agentdesk.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import top.jionjion.agentdesk.dto.session.SessionCreateRequest;
import top.jionjion.agentdesk.dto.session.SessionProjectBindRequest;
import top.jionjion.agentdesk.dto.session.SessionMemoryModeRequest;
import top.jionjion.agentdesk.dto.session.SessionResponse;
import top.jionjion.agentdesk.service.SessionService;

import java.util.List;

/**
 * 会话管理控制器
 *
 * @author Jion
 */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    /**
     * 创建新会话
     */
    @PostMapping
    public SessionResponse create(@RequestBody SessionCreateRequest request) {
        try {
            return sessionService.create(request.title(), request.projectId());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * 列出当前用户的所有会话
     */
    @GetMapping
    public List<SessionResponse> listAll() {
        return sessionService.listByUser();
    }

    /**
     * 获取会话详情
     */
    @GetMapping("/{id}")
    public SessionResponse get(@PathVariable String id) {
        SessionResponse response = sessionService.get(id);
        if (response == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在");
        }
        return response;
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        sessionService.delete(id);
    }

    /**
     * 批量删除会话
     */
    @DeleteMapping("/batch")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBatch(@RequestBody List<String> ids) {
        sessionService.deleteBatch(ids);
    }

    /**
     * 更新会话标题
     */
    @PutMapping("/{id}")
    public SessionResponse updateTitle(@PathVariable String id,
                                       @RequestBody SessionCreateRequest request) {
        SessionResponse response = sessionService.updateTitle(id, request.title());
        if (response == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在");
        }
        return response;
    }

    /**
     * 绑定/解绑会话项目。projectId 为 null 表示解绑。
     */
    @PutMapping("/{id}/project")
    public SessionResponse bindProject(@PathVariable String id,
                                       @RequestBody SessionProjectBindRequest request) {
        SessionResponse response;
        try {
            response = sessionService.bindProject(id, request.projectId());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        if (response == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在");
        }
        return response;
    }

    @PutMapping("/{id}/memory-mode")
    public SessionResponse updateMemoryMode(@PathVariable String id,
                                            @RequestBody SessionMemoryModeRequest request) {
        SessionResponse response;
        try {
            response = sessionService.updateMemoryMode(id, request.memoryMode());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        if (response == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在");
        return response;
    }
}
