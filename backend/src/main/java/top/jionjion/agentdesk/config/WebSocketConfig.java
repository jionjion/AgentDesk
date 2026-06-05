package top.jionjion.agentdesk.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
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

    /**
     * 放开 WebSocket 文本消息缓冲上限。
     * <p>
     * 容器默认仅 8KB，客户端回传较大命令结果（如读取文件内容）时，单帧文本超过 8KB
     * 会被 Tomcat 在帧重组阶段直接关闭连接，表现为后端 "客户端已断开连接"。
     * 这里放宽到 1MB，与执行器侧 maxOutputChars / 业务截断 maxResultSize 上限相匹配。
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        int oneMb = 1024 * 1024;
        container.setMaxTextMessageBufferSize(oneMb);
        container.setMaxBinaryMessageBufferSize(oneMb);
        return container;
    }
}
