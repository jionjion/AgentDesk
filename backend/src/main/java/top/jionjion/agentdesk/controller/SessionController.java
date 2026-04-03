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

    @PostMapping
    public SessionResponse create(@RequestBody SessionCreateRequest request) {
        return sessionService.create(request.title());
    }

    @GetMapping
    public List<SessionResponse> listAll() {
        return sessionService.listAll();
    }

    @GetMapping("/{id}")
    public SessionResponse get(@PathVariable String id) {
        SessionResponse response = sessionService.get(id);
        if (response == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在");
        }
        return response;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        sessionService.delete(id);
    }

    @PutMapping("/{id}")
    public SessionResponse updateTitle(@PathVariable String id, @RequestBody SessionCreateRequest request) {
        SessionResponse response = sessionService.updateTitle(id, request.title());
        if (response == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在");
        }
        return response;
    }
}
