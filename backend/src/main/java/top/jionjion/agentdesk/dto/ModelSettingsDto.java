package top.jionjion.agentdesk.dto;

public record ModelSettingsDto(
        String provider,
        String modelName,
        Double temperature,
        Integer maxTokens,
        Double topP,
        String systemPrompt
) {
    public static ModelSettingsDto defaults() {
        return new ModelSettingsDto("dashscope", "qwen-plus", 0.7, 4096, 0.9, "");
    }
}
