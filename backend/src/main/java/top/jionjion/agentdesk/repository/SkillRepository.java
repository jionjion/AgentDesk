package top.jionjion.agentdesk.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.jionjion.agentdesk.entity.Skill;

import java.util.List;

/**
 * 技能 — JPA 持久层
 *
 * @author Jion
 */
public interface SkillRepository extends JpaRepository<Skill, String> {

    /**
     * 查询内置技能或指定用户的技能
     *
     * @param userId 用户ID
     * @return 技能列表
     */
    List<Skill> findByBuiltinTrueOrUserId(Long userId);

    /**
     * 检查指定ID的技能是否为内置技能
     *
     * @param id 技能ID
     * @return 是否为内置技能
     */
    boolean existsByIdAndBuiltinTrue(String id);
}
