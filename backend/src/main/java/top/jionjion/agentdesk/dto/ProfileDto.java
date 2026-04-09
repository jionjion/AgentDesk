package top.jionjion.agentdesk.dto;

import top.jionjion.agentdesk.entity.User;

public record ProfileDto(Long id, String username, String nickname, String avatar) {

    public static ProfileDto from(User user) {
        return new ProfileDto(user.getId(), user.getUsername(), user.getNickname(), user.getAvatar());
    }
}
