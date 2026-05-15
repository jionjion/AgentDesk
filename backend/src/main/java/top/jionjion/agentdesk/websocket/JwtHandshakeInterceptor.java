package top.jionjion.agentdesk.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import top.jionjion.agentdesk.service.JwtService;

import java.util.Map;

/**
 * WebSocket 握手拦截器: 从 URL 参数提取 JWT 并验证, 将用户信息存入 Session 属性。
 *
 * @author Jion
 */
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JwtHandshakeInterceptor.class);

    public static final String ATTR_USER_ID = "userId";
    public static final String ATTR_USERNAME = "username";

    private final JwtService jwtService;

    public JwtHandshakeInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = extractToken(request);
        if (token == null || !jwtService.isTokenValid(token)) {
            log.warn("WebSocket 握手失败: 无效的 Token");
            return false;
        }

        Long userId = jwtService.getUserId(token);
        String username = jwtService.getUsername(token);
        attributes.put(ATTR_USER_ID, userId);
        attributes.put(ATTR_USERNAME, username);

        log.debug("WebSocket 握手成功: userId={}, username={}", userId, username);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需额外处理
    }

    private String extractToken(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            return servletRequest.getServletRequest().getParameter("token");
        }
        // 从 URI query 参数中提取
        String query = request.getURI().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2 && "token".equals(kv[0])) {
                    return kv[1];
                }
            }
        }
        return null;
    }
}
