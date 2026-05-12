package top.jionjion.agentdesk.dto.auth;

/**
 * 注册请求
 *
 * @param username 用户名
 * @param password 密码
 * @param nickname 昵称
 * @author Jion
 */
public record RegisterRequest(String username, String password, String nickname) {
}
