package top.jionjion.agentdesk.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 当前认证用户主体, 存入 SecurityContext 的 principal
 */
@Getter
@AllArgsConstructor
public class UserPrincipal {

    /**
     * 用户 ID
     */
    private final Long id;

    /**
     * 用户名
     */
    private final String username;
}
