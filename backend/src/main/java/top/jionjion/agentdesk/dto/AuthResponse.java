package top.jionjion.agentdesk.dto;

/**
 * 认证响应
 *
 * @author Jion
 */
public record AuthResponse(Long id, String username, String nickname, String avatar, String token) {
}
