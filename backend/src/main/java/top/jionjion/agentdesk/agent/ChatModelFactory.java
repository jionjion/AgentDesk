package top.jionjion.agentdesk.agent;

import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.GenerateOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import top.jionjion.agentdesk.dto.ModelSettingsDto;

/**
 * ChatModel 工厂: 根据用户设置创建 DashScopeChatModel 实例。
 * <p>
 * 主 Agent 和所有子 Agent 共用同一个 ChatModel 实例,
 * 确保切换模型时所有 Agent 统一生效。
 *
 * @author Jion
 */
@Component
public class ChatModelFactory {

    @Value("${agentscope.dashscope.api-key}")
    private String defaultApiKey;

    /**
     * 根据用户模型设置创建 DashScopeChatModel。
     *
     * @param settings 用户模型配置
     * @param apiKey   用户自定义 API Key, 为 null 时使用系统默认 Key
     * @return DashScopeChatModel 实例
     */
    public DashScopeChatModel create(ModelSettingsDto settings, String apiKey) {
        String effectiveKey = (apiKey != null && !apiKey.isBlank()) ? apiKey : defaultApiKey;

        GenerateOptions options = GenerateOptions.builder()
                .temperature(settings.temperature())
                .topP(settings.topP())
                .maxTokens(settings.maxTokens())
                .build();

        return DashScopeChatModel.builder()
                .apiKey(effectiveKey)
                .modelName(settings.modelId())
                .enableThinking(Boolean.TRUE.equals(settings.enableThinking()))
                .defaultOptions(options)
                .build();
    }

    /**
     * 使用默认 API Key 创建模型（内部服务使用, 如 TitleGenerationService）
     */
    public DashScopeChatModel create(ModelSettingsDto settings) {
        return create(settings, null);
    }
}
