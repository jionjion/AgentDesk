package top.jionjion.agentdesk.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import top.jionjion.agentdesk.dto.AuthResponse;
import top.jionjion.agentdesk.dto.LoginRequest;
import top.jionjion.agentdesk.dto.RegisterRequest;
import top.jionjion.agentdesk.entity.User;
import top.jionjion.agentdesk.repository.UserRepository;
import top.jionjion.agentdesk.security.UserContext;

/**
 * 认证业务逻辑
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * 注册
     */
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        }

        User user = new User(
                request.username(),
                passwordEncoder.encode(request.password()),
                request.nickname()
        );
        userRepository.save(user);

        String token = jwtService.generateToken(user.getId(), user.getUsername());
        return new AuthResponse(user.getId(), user.getUsername(), user.getNickname(), user.getAvatar(), token);
    }

    /**
     * 登录
     */
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }

        String token = jwtService.generateToken(user.getId(), user.getUsername());
        return new AuthResponse(user.getId(), user.getUsername(), user.getNickname(), user.getAvatar(), token);
    }

    /**
     * 获取当前用户信息
     */
    public AuthResponse getCurrentUser() {
        Long userId = UserContext.getUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        return new AuthResponse(user.getId(), user.getUsername(), user.getNickname(), user.getAvatar(), null);
    }
}
