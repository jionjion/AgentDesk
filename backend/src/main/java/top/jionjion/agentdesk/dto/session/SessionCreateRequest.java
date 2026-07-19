package top.jionjion.agentdesk.dto.session;

/**
 * 创建会话请求
 *
 * @author Jion
 */
public record SessionCreateRequest(
        /** 会话标题 */
        String title,
        /** 可选关联项目ID */
        String projectId) {
}
