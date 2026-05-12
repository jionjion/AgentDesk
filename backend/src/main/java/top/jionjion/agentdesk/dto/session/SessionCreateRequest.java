package top.jionjion.agentdesk.dto.session;

/**
 * 创建会话请求
 *
 * @param title 会话标题
 * @author Jion
 */
public record SessionCreateRequest(
        /** 会话标题 */
        String title) {
}
