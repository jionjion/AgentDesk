package top.jionjion.agentdesk.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import top.jionjion.agentdesk.dto.SessionCreateRequest;
import top.jionjion.agentdesk.dto.SessionResponse;
import top.jionjion.agentdesk.session.SessionService;

import java.util.List;

/**
 * 会话管理控制器
 */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    /** 创建新会话 */
    @PostMapping
    public SessionResponse create(@RequestBody SessionCreateRequest request,
                                  @RequestAttribute("userId") Long userId) {
        return sessionService.create(request.title(), userId);
    }

    /** 列出当前用户的所有会话 */
    @GetMapping
    public List<SessionResponse> listAll(@RequestAttribute("userId") Long userId) {
        return sessionService.listByUser(userId);
    }

    /** 获取会话详情 */
    @GetMapping("/{id}")
    public SessionResponse get(@PathVariable String id,
                               @RequestAttribute("userId") Long userId) {
        SessionResponse response = sessionService.get(id, userId);
        if (response == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在");
        }
        return response;
    }

    /** 删除会话 */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id,
                       @RequestAttribute("userId") Long userId) {
        sessionService.delete(id, userId);
    }

    /** 更新会话标题 */
    @PutMapping("/{id}")
    public SessionResponse updateTitle(@PathVariable String id,
                                       @RequestBody SessionCreateRequest request,
                                       @RequestAttribute("userId") Long userId) {
        SessionResponse response = sessionService.updateTitle(id, request.title(), userId);
        if (response == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在");
        }
        return response;
    }
}
