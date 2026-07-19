package top.jionjion.agentdesk.dto.project;

/**
 * 更新项目请求
 *
 * @author Jion
 */
public record ProjectUpdateRequest(
        /** 项目名称 */
        String name,
        /** 项目说明 */
        String description,
        /** 项目级指令 */
        String instructions) {
}
