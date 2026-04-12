package top.jionjion.agentdesk.service;

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.GenerateOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 使用 AI 模型根据用户消息生成会话标题
 *
 * @author Jion
 */
@Service
public class TitleGenerationService {

    private static final Logger log = LoggerFactory.getLogger(TitleGenerationService.class);

    private final DashScopeChatModel model;

    public TitleGenerationService(@Value("${agentscope.dashscope.api-key}") String apiKey) {
        GenerateOptions options = GenerateOptions.builder()
                .temperature(0.3)
                .maxTokens(50)
                .build();
        this.model = DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName("qwen-turbo")
                .defaultOptions(options)
                .build();
    }

    /**
     * 根据用户消息生成简短的会话标题
     *
     * @param userMessage 用户的第一条消息
     * @return 生成的标题 (不超过20字)
     */
    public String generateTitle(String userMessage) {
        try {
            Msg systemMsg = Msg.builder()
                    .role(MsgRole.SYSTEM)
                    .textContent("你是一个标题生成器。根据用户的问题，生成一个简短的会话标题（不超过15个字）。" +
                            "只输出标题本身，不要加引号、序号或任何其他内容。")
                    .build();
            Msg userMsg = Msg.builder()
                    .role(MsgRole.USER)
                    .textContent(userMessage)
                    .build();

            // 使用 stream 接口收集完整响应
            StringBuilder sb = new StringBuilder();
            List<ChatResponse> responses = model.stream(
                    List.of(systemMsg, userMsg), Collections.emptyList(), null
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

            String title = sb.toString().trim();
            if (!title.isEmpty()) {
                if (title.length() > 20) {
                    title = title.substring(0, 20);
                }
                return title;
            }
        } catch (Exception e) {
            log.warn("生成标题失败: {}", e.getMessage());
        }
        return null;
    }
}
