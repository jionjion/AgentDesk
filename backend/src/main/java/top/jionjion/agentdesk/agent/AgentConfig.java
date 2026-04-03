package top.jionjion.agentdesk.agent;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.tool.Toolkit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 配置类
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

    @Bean
    public Toolkit toolkit() {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new SimpleTools());
        return toolkit;
    }

    @Bean
    public ReActAgent assistant(DashScopeChatModel model, Toolkit toolkit) {
        return ReActAgent.builder()
                .name("assistant")
                .sysPrompt("你是一个名为 Assistant 的智能助手。你可以帮助用户回答问题、获取当前时间、进行数学计算等。请用中文回答。")
                .model(model)
                .toolkit(toolkit)
                .maxIters(10)
                .build();
    }
}
