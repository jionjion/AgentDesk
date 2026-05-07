package top.jionjion.agentdesk.dto.auth;

/**
 * 注册请求
 *
 * @author Jion
 */
public record RegisterRequest(String username, String password, String nickname) {
}
