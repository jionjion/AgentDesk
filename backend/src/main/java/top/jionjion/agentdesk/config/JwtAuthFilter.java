package top.jionjion.agentdesk.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import top.jionjion.agentdesk.service.JwtService;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器: 从 Header 提取并验证 Token, 设置 SecurityContext
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtService.isTokenValid(token)) {
                Long userId = jwtService.getUserId(token);
                String username = jwtService.getUsername(token);

                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        userId, null, List.of()
                );
                SecurityContextHolder.getContext().setAuthentication(auth);

                // 同时设置为 request attribute, 方便 Controller 取用
                request.setAttribute("userId", userId);
                request.setAttribute("username", username);
            }
        }

        filterChain.doFilter(request, response);
    }
}
