package top.jionjion.agentdesk.security;

/**
 * 当前认证用户主体, 存入 SecurityContext 的 principal
 */
public record UserPrincipal(Long id, String username) {
}
