package top.jionjion.agentdesk.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前用户上下文, 从 SecurityContext 中获取认证信息.
 * <p>
 * 用法: {@code UserContext.getUserId()}, {@code UserContext.getUsername()} 等
 */
public final class UserContext {

    private UserContext() {
    }

    /**
     * 获取当前认证用户主体
     */
    public static UserPrincipal getPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal;
        }
        throw new IllegalStateException("未认证");
    }

    /**
     * 获取当前用户 ID
     */
    public static Long getUserId() {
        return getPrincipal().id();
    }

    /**
     * 获取当前用户名
     */
    public static String getUsername() {
        return getPrincipal().username();
    }

    /**
     * 当前请求是否已认证
     */
    public static boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof UserPrincipal;
    }

    /**
     * 获取当前用户 ID, 未认证时返回 null (适用于可选认证场景)
     */
    public static Long getUserIdOrNull() {
        return isAuthenticated() ? getPrincipal().id() : null;
    }
}
