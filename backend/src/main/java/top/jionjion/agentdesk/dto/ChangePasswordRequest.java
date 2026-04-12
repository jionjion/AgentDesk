package top.jionjion.agentdesk.dto;

/**
 * 修改密码请求
 *
 * @author Jion
 */
public record ChangePasswordRequest(String oldPassword, String newPassword) {
}
