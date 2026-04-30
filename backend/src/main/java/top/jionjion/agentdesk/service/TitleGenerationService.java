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
import top.jionjion.agentdesk.agent.ChatModelFactory;
import top.jionjion.agentdesk.dto.ModelSettingsDto;

import java.time.Duration;
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

    /**
     * 标题生成超时时间（30秒），避免模型服务不可用时长时间阻塞
     */
    private static final Duration TITLE_TIMEOUT = Duration.ofSeconds(30);

    private final DashScopeChatModel model;
    private final GenerateOptions titleOptions;

    public TitleGenerationService(ChatModelFactory chatModelFactory) {
        // 标题生成始终使用 qwen-turbo（轻量快速、低成本）
        ModelSettingsDto titleSettings = new ModelSettingsDto(
                "qwen-turbo", 0.3, 50, 0.9, false, ""
        );
        this.model = chatModelFactory.create(titleSettings);
        this.titleOptions = GenerateOptions.builder()
                .executionConfig(ExecutionConfig.builder()
                        .timeout(TITLE_TIMEOUT)
                        .maxAttempts(1)
                        .build())
                .build();
    }

    /**
     * 根据用户消息生成简短的会话标题
     *
     * @param userMessage 用户的第一条消息
     * @return 生成的标题 (不超过20字)
     */
    public String generateTitle(String userMessage) {
        long startTime = System.currentTimeMillis();
        try {
            log.debug("开始生成标题, 模型: qwen-turbo, 消息长度: {}", userMessage.length());

            Msg systemMsg = Msg.builder()
                    .role(MsgRole.SYSTEM)
                    .textContent("你是一个标题生成器。根据用户的问题，生成一个简短的会话标题（不超过15个字）。" +
                            "只输出标题本身，不要加引号、序号或任何其他内容。")
                    .build();
            Msg userMsg = Msg.builder()
                    .role(MsgRole.USER)
                    .textContent(userMessage)
                    .build();

            // 使用 stream 接口收集完整响应, 传入 titleOptions 以应用 30 秒超时
            StringBuilder sb = new StringBuilder();
            List<ChatResponse> responses = model.stream(
                    List.of(systemMsg, userMsg), Collections.emptyList(), titleOptions
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
            long elapsed = System.currentTimeMillis() - startTime;
            if (!title.isEmpty()) {
                if (title.length() > 20) {
                    title = title.substring(0, 20);
                }
                log.debug("标题生成成功, 耗时: {}ms, 标题: {}", elapsed, title);
                return title;
            }
            log.warn("标题生成返回空内容, 模型: qwen-turbo, 耗时: {}ms", elapsed);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            if (root instanceof java.util.concurrent.TimeoutException
                    || (root.getMessage() != null && root.getMessage().contains("timeout"))) {
                log.warn("标题生成超时, 模型: qwen-turbo, 耗时: {}ms. 请检查模型服务可用性或网络连接", elapsed);
            } else if (root instanceof java.net.ConnectException || root instanceof java.net.UnknownHostException) {
                log.error("标题生成失败: 无法连接模型服务, 耗时: {}ms, 原因: {}", elapsed, root.getMessage());
            } else {
                log.warn("标题生成失败, 模型: qwen-turbo, 耗时: {}ms, 异常类型: {}, 原因: {}",
                        elapsed, root.getClass().getSimpleName(), root.getMessage());
            }
        }
        return null;
    }
}
