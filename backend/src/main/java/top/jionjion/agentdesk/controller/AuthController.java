package top.jionjion.agentdesk.controller;

import org.springframework.web.bind.annotation.*;
import top.jionjion.agentdesk.dto.AuthResponse;
import top.jionjion.agentdesk.dto.LoginRequest;
import top.jionjion.agentdesk.dto.RegisterRequest;
import top.jionjion.agentdesk.service.AuthService;

/**
 * 认证控制器: 注册/登录/获取用户信息
 *
 * @author Jion
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public AuthResponse register(@RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public AuthResponse me() {
        return authService.getCurrentUser();
    }
}
