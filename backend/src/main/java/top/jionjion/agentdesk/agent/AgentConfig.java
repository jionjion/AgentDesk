package top.jionjion.agentdesk.agent;

import org.springframework.context.annotation.Configuration;

/**
 * Agent 配置类
 * <p>
 * DashScopeChatModel 不再作为全局 Bean 注册,
 * 改由 {@link ChatModelFactory} 根据用户设置按需创建。
 *
 * @author Jion
 */
@Configuration
public class AgentConfig {
    // ChatModel 创建已迁移到 ChatModelFactory
    // 保留此配置类以备后续扩展
}
