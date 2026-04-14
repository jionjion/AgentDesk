package top.jionjion.agentdesk.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 用户技能偏好实体
 *
 * @author Jion
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "user_skill_preferences", schema = "agent_desk",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "skill_id"}))
public class UserSkillPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "skill_id", nullable = false)
    private String skillId;

    @Column(nullable = false)
    private boolean enabled;

    public UserSkillPreference(Long userId, String skillId, boolean enabled) {
        this.userId = userId;
        this.skillId = skillId;
        this.enabled = enabled;
    }
}
