package top.jionjion.agentdesk.dto.auth;

/**
 * 认证响应
 *
 * @author Jion
 */
public record AuthResponse(Long id, String username, String nickname, String avatar, String token) {
}
