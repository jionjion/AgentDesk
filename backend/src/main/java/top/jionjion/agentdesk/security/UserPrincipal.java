package top.jionjion.agentdesk.security;

/**
 * 当前认证用户主体, 存入 SecurityContext 的 principal
 *
 * @param id       用户 ID
 * @param username 用户名
 * @author Jion
 */
public record UserPrincipal(Long id, String username) {

}
