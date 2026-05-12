package top.jionjion.agentdesk.dto.auth;

/**
 * 更新个人资料请求
 *
 * @param nickname 昵称
 * @param avatar   头像地址
 * @author Jion
 */
public record UpdateProfileRequest(String nickname, String avatar) {
}
