package top.jionjion.agentdesk.agent.runtime;

import java.util.List;

/**
 * 与具体 AgentScope 版本无关的 Agent 输入。
 *
 * <p>业务层只负责准备文本和多模态资源地址。v1 的 {@code Msg} 与 v2 的
 * {@code UserMessage} 转换由运行时适配层完成，避免框架消息类型扩散到聊天编排层。
 */
public record AgentInput(String text, List<String> imageUrls) {

    public AgentInput {
        text = text == null ? "" : text;
        imageUrls = imageUrls == null ? List.of() : List.copyOf(imageUrls);
    }

    public static AgentInput text(String text) {
        return new AgentInput(text, List.of());
    }

    public boolean isMultimodal() {
        return !imageUrls.isEmpty();
    }
}
