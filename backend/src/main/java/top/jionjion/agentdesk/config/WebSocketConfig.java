package top.jionjion.agentdesk.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import top.jionjion.agentdesk.websocket.JwtHandshakeInterceptor;
import top.jionjion.agentdesk.websocket.RemoteExecWebSocketHandler;

/**
 * WebSocket 配置: 注册远程执行端点
 *
 * @author Jion
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final RemoteExecWebSocketHandler remoteExecHandler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;
    private final String allowedOrigins;

    public WebSocketConfig(RemoteExecWebSocketHandler remoteExecHandler,
                           JwtHandshakeInterceptor jwtHandshakeInterceptor,
                           @Value("${agentdesk.remote-exec.allowed-origins:http://localhost:*}") String allowedOrigins) {
        this.remoteExecHandler = remoteExecHandler;
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(remoteExecHandler, "/ws/remote-exec")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOriginPatterns(allowedOrigins.split(","));
    }
}
