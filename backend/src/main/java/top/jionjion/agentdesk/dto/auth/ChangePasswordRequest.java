package top.jionjion.agentdesk.dto.auth;

/**
 * 修改密码请求
 *
 * @param oldPassword 旧密码
 * @param newPassword 新密码
 * @author Jion
 */
public record ChangePasswordRequest(String oldPassword, String newPassword) {
}
