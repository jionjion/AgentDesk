package top.jionjion.agentdesk.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.jionjion.agentdesk.entity.UserSettings;

import java.util.Optional;

/**
 * 用户设置 — JPA 持久层
 *
 * @author Jion
 */
public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {

    /**
     * 根据用户ID查询用户设置
     *
     * @param userId 用户ID
     * @return 用户设置
     */
    Optional<UserSettings> findByUserId(Long userId);
}
