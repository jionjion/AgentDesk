package top.jionjion.agentdesk.dto;

/**
 * 模型设置 DTO
 *
 * @param modelId        百炼平台模型 ID, 如 "qwen3.6-plus" (即 DashScope API 的 model 参数)
 * @param temperature    温度 0.0 ~ 2.0
 * @param maxTokens      最大输出 token 数
 * @param topP           Top-P 采样 0.0 ~ 1.0
 * @param enableThinking 是否开启深度思考（需模型支持推理能力）
 * @param systemPrompt   自定义系统提示词
 * @author Jion
 */
public record ModelSettingsDto(
        String modelId,
        Double temperature,
        Integer maxTokens,
        Double topP,
        Boolean enableThinking,
        String systemPrompt
) {
    public static ModelSettingsDto defaults() {
        return new ModelSettingsDto("qwen3.6-plus", 0.7, 4096, 0.9, false, "");
    }
}
