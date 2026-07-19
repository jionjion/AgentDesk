package top.jionjion.agentdesk.dto.session;

/**
 * 会话项目绑定请求。projectId 为 null 表示解绑。
 *
 * @author Jion
 */
public record SessionProjectBindRequest(
        /** 项目ID, null 表示解绑 */
        String projectId) {
}
