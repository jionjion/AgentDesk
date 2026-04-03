package top.jionjion.agentdesk.agent;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.tool.Toolkit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Agent 集成测试 - 直接调用阿里云百炼平台验证 Agent 效果
 */
@SpringBootTest
class AgentTest {

    @Autowired
    private Environment environment;


    @Test
    void testAgentChat() {
        // 构建模型
        DashScopeChatModel model = DashScopeChatModel.builder()
                .apiKey(environment.getProperty("DASHSCOPE_API_KEY"))
                .modelName("qwen-plus")
                .build();

        // 注册工具
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new SimpleTools());

        // 创建 Agent
        ReActAgent agent = ReActAgent.builder()
                .name("test-assistant")
                .sysPrompt("你是一个智能助手, 请用中文回答。")
                .model(model)
                .toolkit(toolkit)
                .build();

        // 测试简单对话
        Msg response = agent.call(Msg.builder()
                .textContent("你好, 请介绍一下你自己")
                .build()).block();

        assertNotNull(response);
        System.out.println("Agent 回复: " + response.getTextContent());
    }

    @Test
    void testAgentWithTool() {
        DashScopeChatModel model = DashScopeChatModel.builder()
                .apiKey(environment.getProperty("DASHSCOPE_API_KEY"))
                .modelName("qwen-plus")
                .build();

        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new SimpleTools());

        ReActAgent agent = ReActAgent.builder()
                .name("test-assistant")
                .sysPrompt("你是一个智能助手, 请用中文回答。当用户询问时间时, 请调用工具获取。")
                .model(model)
                .toolkit(toolkit)
                .build();

        // 测试工具调用
        Msg response = agent.call(Msg.builder()
                .textContent("现在北京时间几点了?")
                .build()).block();

        assertNotNull(response);
        System.out.println("Agent 回复(工具调用): " + response.getTextContent());
    }
}
