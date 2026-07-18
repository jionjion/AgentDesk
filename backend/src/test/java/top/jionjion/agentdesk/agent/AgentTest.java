package top.jionjion.agentdesk.agent;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.jionjion.agentdesk.agent.core.ChatModelFactory;
import top.jionjion.agentdesk.agent.tool.SimpleTools;
import top.jionjion.agentdesk.dto.settings.ModelSettingsDto;
import top.jionjion.agentdesk.repository.FileRecordRepository;
import top.jionjion.agentdesk.service.OssService;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Agent 集成测试 - 直接调用阿里云百炼平台验证 Agent 效果
 */
@SpringBootTest
@Tag("integration")
class AgentTest {

    @Autowired
    private ChatModelFactory chatModelFactory;

    @Autowired
    private FileRecordRepository fileRecordRepository;

    @Autowired
    private OssService ossService;


    @Test
    void testAgentChat() {
        DashScopeChatModel model = chatModelFactory.create(ModelSettingsDto.defaults());
        // 注册工具
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new SimpleTools(fileRecordRepository, ossService));

        // 创建 Agent
        HarnessAgent agent = HarnessAgent.builder()
                .name("test-assistant")
                .sysPrompt("你是一个智能助手, 请用中文回答。")
                .model(model)
                .toolkit(toolkit)
                .build();

        // 测试简单对话
        Msg response = agent.call(new UserMessage("你好, 请介绍一下你自己"),
                RuntimeContext.builder().sessionId("integration-chat").build()).block();

        assertNotNull(response);
        System.out.println("Agent 回复: " + response.getTextContent());
    }

    @Test
    void testAgentWithTool() {
        DashScopeChatModel model = chatModelFactory.create(ModelSettingsDto.defaults());
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new SimpleTools(fileRecordRepository, ossService));

        HarnessAgent agent = HarnessAgent.builder()
                .name("test-assistant")
                .sysPrompt("你是一个智能助手, 请用中文回答。当用户询问时间时, 请调用工具获取。")
                .model(model)
                .toolkit(toolkit)
                .build();

        // 测试工具调用
        Msg response = agent.call(new UserMessage("现在北京时间几点了?"),
                RuntimeContext.builder().sessionId("integration-tool").build()).block();

        assertNotNull(response);
        System.out.println("Agent 回复(工具调用): " + response.getTextContent());
    }
}
