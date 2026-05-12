package top.jionjion.agentdesk.dto.auth;

/**
 * 登录请求
 *
 * @param username 用户名
 * @param password 密码
 * @author Jion
 */
public record LoginRequest(String username, String password) {
}
