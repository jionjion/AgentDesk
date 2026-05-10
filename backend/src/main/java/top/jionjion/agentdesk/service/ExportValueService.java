package top.jionjion.agentdesk.service;

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.DashScopeChatModel;
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
 * 导出价值判断服务 - 使用轻量 AI 模型判断对话是否值得沉淀到 Obsidian
 *
 * @author Jion
 */
@Service
public class ExportValueService {

    private static final Logger log = LoggerFactory.getLogger(ExportValueService.class);

    private static final Duration JUDGE_TIMEOUT = Duration.ofSeconds(10);

    private static final String SYSTEM_PROMPT = """
            你是一个对话价值评估器。判断以下对话内容是否有足够的知识价值，值得保存到笔记中。
            
            值得保存的情况: 包含有价值的技术方案、问题解决过程、代码实现、架构设计、配置方法、
            学习笔记、工作总结、创意方案等实质性的知识内容。
            
            不值得保存的情况: 纯闲聊问候、简单的是/否回答、测试消息、无实质内容的短对话（少于3轮且无信息量）、
            纯粹的格式化/排版请求。
            
            只回复 YES 或 NO，不要输出任何其他内容。""";

    private final DashScopeChatModel model;
    private final GenerateOptions judgeOptions;

    public ExportValueService(ChatModelFactory chatModelFactory) {
        ModelSettingsDto settings = new ModelSettingsDto(
                "qwen-turbo", 0.1, 5, 0.9, false, ""
        );
        this.model = chatModelFactory.create(settings);
        this.judgeOptions = GenerateOptions.builder()
                .executionConfig(ExecutionConfig.builder()
                        .timeout(JUDGE_TIMEOUT)
                        .maxAttempts(1)
                        .build())
                .build();
    }

    /**
     * 判断对话内容是否有足够价值值得导出
     *
     * @param conversationSummary 对话摘要（前几轮对话拼接）
     * @return true 表示值得导出, false 表示不值得; 判断失败时默认返回 true (保守策略)
     */
    public boolean isWorthExporting(String conversationSummary) {
        try {
            Msg systemMsg = Msg.builder()
                    .role(MsgRole.SYSTEM)
                    .textContent(SYSTEM_PROMPT)
                    .build();
            Msg userMsg = Msg.builder()
                    .role(MsgRole.USER)
                    .textContent(conversationSummary)
                    .build();

            StringBuilder sb = new StringBuilder();
            List<ChatResponse> responses = model.stream(
                    List.of(systemMsg, userMsg), Collections.emptyList(), judgeOptions
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
            boolean worthy = result.contains("YES");
            log.info("对话价值判断: worthy={}, summary='{}'", worthy,
                    conversationSummary.length() > 80 ? conversationSummary.substring(0, 80) + "..." : conversationSummary);
            return worthy;
        } catch (Exception e) {
            log.warn("对话价值判断失败, 默认执行导出: {}", e.getMessage());
            return true;
        }
    }
}
