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

    /**
     * 根据用户ID查询技能偏好列表
     *
     * @param userId 用户ID
     * @return 技能偏好列表
     */
    List<UserSkillPreference> findByUserId(Long userId);

    /**
     * 根据用户ID和技能ID查询技能偏好
     *
     * @param userId  用户ID
     * @param skillId 技能ID
     * @return 技能偏好Optional
     */
    Optional<UserSkillPreference> findByUserIdAndSkillId(Long userId, String skillId);

    /**
     * 根据用户ID和技能ID删除技能偏好
     *
     * @param userId  用户ID
     * @param skillId 技能ID
     */
    void deleteByUserIdAndSkillId(Long userId, String skillId);
}
