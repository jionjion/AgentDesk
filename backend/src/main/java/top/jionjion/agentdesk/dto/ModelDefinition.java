package top.jionjion.agentdesk.dto;

import java.util.List;

/**
 * 模型元数据定义（百炼平台）
 *
 * @param id                模型 ID, 即 DashScope API 的 model 参数, 如 "qwen3.6-plus"
 * @param displayName       前端展示名, 如 "Qwen3.6 Plus"
 * @param group             分组, 如 "旗舰模型"、"轻量快速"
 * @param inputModalities   支持的输入模态, 如 ["text"], ["text","image","video"]
 * @param supportsReasoning 是否支持推理/深度思考
 * @param maxContextWindow  最大上下文窗口 (token)
 * @param maxOutputTokens   最大输出 token
 * @param description       简要说明
 * @author Jion
 */
public record ModelDefinition(
        String id,
        String displayName,
        String group,
        List<String> inputModalities,
        boolean supportsReasoning,
        int maxContextWindow,
        int maxOutputTokens,
        String description
) {}
