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

    List<Skill> findByBuiltinTrue();

    List<Skill> findByUserId(Long userId);

    List<Skill> findByBuiltinTrueOrUserId(Long userId);

    boolean existsByIdAndBuiltinTrue(String id);
}
