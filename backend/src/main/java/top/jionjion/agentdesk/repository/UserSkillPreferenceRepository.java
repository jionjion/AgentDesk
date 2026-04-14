package top.jionjion.agentdesk.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.jionjion.agentdesk.entity.UserSkillPreference;

import java.util.List;
import java.util.Optional;

/**
 * 用户技能偏好 — JPA 持久层
 *
 * @author Jion
 */
public interface UserSkillPreferenceRepository extends JpaRepository<UserSkillPreference, Long> {

    List<UserSkillPreference> findByUserId(Long userId);

    Optional<UserSkillPreference> findByUserIdAndSkillId(Long userId, String skillId);

    void deleteByUserIdAndSkillId(Long userId, String skillId);
}
