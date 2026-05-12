package top.jionjion.agentdesk.dto.auth;

/**
 * 认证响应
 *
 * @param id       用户ID
 * @param username 用户名
 * @param nickname 昵称
 * @param avatar   头像地址
 * @param token    认证令牌
 * @author Jion
 */
public record AuthResponse(Long id, String username, String nickname, String avatar, String token) {
}
