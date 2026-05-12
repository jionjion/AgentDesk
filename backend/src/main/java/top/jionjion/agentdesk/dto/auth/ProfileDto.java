package top.jionjion.agentdesk.dto.auth;

/**
 * 用户个人资料 DTO
 *
 * @param id       用户ID
 * @param username 用户名
 * @param nickname 昵称
 * @param avatar   头像地址
 * @author Jion
 */
public record ProfileDto(Long id, String username, String nickname, String avatar) {

}
