package top.jionjion.agentdesk.agent;

import io.agentscope.core.model.DashScopeChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 配置类: 仅提供共享的无状态 Bean
 *
 * @author Jion
 */
@Configuration
public class AgentConfig {

    @Value("${agentscope.dashscope.api-key}")
    private String apiKey;

    @Bean
    public DashScopeChatModel dashScopeChatModel() {
        return DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName("qwen-plus")
                .build();
    }
}
