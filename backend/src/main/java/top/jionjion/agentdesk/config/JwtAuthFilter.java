package top.jionjion.agentdesk.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import top.jionjion.agentdesk.security.UserPrincipal;
import top.jionjion.agentdesk.service.JwtService;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器: 从 Header 或 URL 参数提取并验证 Token, 设置 SecurityContext.
 * <p>
 * SSE (EventSource) 无法设置自定义 Header, 因此也支持 ?token=xxx 方式传递.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null && jwtService.isTokenValid(token)) {
            Long userId = jwtService.getUserId(token);
            String username = jwtService.getUsername(token);
            UserPrincipal principal = new UserPrincipal(userId, username);

            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    principal, null, List.of()
            );
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 优先从 Authorization Header 提取, 其次从 URL 参数 token 提取
     */
    private String resolveToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return request.getParameter("token");
    }
}
