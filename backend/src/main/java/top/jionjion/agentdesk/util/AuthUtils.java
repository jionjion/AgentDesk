package top.jionjion.agentdesk.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 认证工具类, 从 SecurityContext 中获取当前用户信息
 */
public final class AuthUtils {

    private AuthUtils() {
    }

    /**
     * 获取当前登录用户ID
     */
    public static Long getUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long userId) {
            return userId;
        }
        throw new IllegalStateException("未认证");
    }
}
