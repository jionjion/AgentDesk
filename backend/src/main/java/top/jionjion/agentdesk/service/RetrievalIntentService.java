package top.jionjion.agentdesk.service;

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.model.GenerateOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import top.jionjion.agentdesk.agent.core.ChatModelFactory;
import top.jionjion.agentdesk.dto.settings.ModelSettingsDto;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * 检索意图判断服务 - 使用轻量 AI 模型判断用户查询是否需要知识库检索
 *
 * @author Jion
 */
@Service
public class RetrievalIntentService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalIntentService.class);

    private static final Duration INTENT_TIMEOUT = Duration.ofSeconds(10);

    private static final String SYSTEM_PROMPT = """
            你是一个意图分类器。判断用户的消息是否需要从外部知识库中检索信息来回答。
            
            需要检索的情况: 用户询问特定的事实、文档内容、技术细节、项目信息等需要参考资料才能准确回答的问题。
            不需要检索的情况: 闲聊、问候、简单计算、代码生成、创意写作、角色扮演、通用常识等。
            
            只回复 YES 或 NO，不要输出任何其他内容。""";

    private final DashScopeChatModel model;
    private final GenerateOptions intentOptions;

    public RetrievalIntentService(ChatModelFactory chatModelFactory) {
        ModelSettingsDto settings = new ModelSettingsDto(
                "qwen-turbo", 0.1, 5, 0.9, false, ""
        );
        this.model = chatModelFactory.create(settings);
        this.intentOptions = GenerateOptions.builder()
                .executionConfig(ExecutionConfig.builder()
                        .timeout(INTENT_TIMEOUT)
                        .maxAttempts(1)
                        .build())
                .build();
    }

    /**
     * 判断用户消息是否需要知识库检索
     *
     * @param userMessage 用户消息
     * @return true 表示需要检索, false 表示不需要; 判断失败时默认返回 true (保守策略)
     */
    public boolean needsRetrieval(String userMessage) {
        try {
            Msg systemMsg = Msg.builder()
                    .role(MsgRole.SYSTEM)
                    .textContent(SYSTEM_PROMPT)
                    .build();
            Msg userMsg = Msg.builder()
                    .role(MsgRole.USER)
                    .textContent(userMessage)
                    .build();

            StringBuilder sb = new StringBuilder();
            List<ChatResponse> responses = model.stream(
                    List.of(systemMsg, userMsg), Collections.emptyList(), intentOptions
            ).collectList().block();

            if (responses != null) {
                for (ChatResponse resp : responses) {
                    if (resp.getContent() != null) {
                        for (ContentBlock block : resp.getContent()) {
                            if (block instanceof TextBlock tb) {
                                sb.append(tb.getText());
                            }
                        }
                    }
                }
            }

            String result = sb.toString().trim().toUpperCase();
            boolean needs = result.contains("YES");
            log.info("检索意图判断: query='{}', result={}", 
                    userMessage.length() > 50 ? userMessage.substring(0, 50) + "..." : userMessage, needs);
            return needs;
        } catch (Exception e) {
            log.warn("检索意图判断失败, 默认执行检索: {}", e.getMessage());
            return true;
        }
    }
}
