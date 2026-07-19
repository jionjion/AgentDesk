package top.jionjion.agentdesk.dto.project;

/**
 * 项目响应
 *
 * @author Jion
 */
public record ProjectResponse(
        /** 项目ID */
        String id,
        /** 项目名称 */
        String name,
        /** 项目说明 */
        String description,
        /** 项目级指令 */
        String instructions,
        /** 创建时间戳 */
        long createdAt,
        /** 更新时间戳 */
        long updatedAt) {
}
