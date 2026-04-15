package top.jionjion.agentdesk.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 定时任务实体
 *
 * @author Jion
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "scheduled_tasks", schema = "agent_desk")
public class ScheduledTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 512)
    private String description;

    @Column(columnDefinition = "text", nullable = false)
    private String prompt;

    @Column(name = "cron_expression", nullable = false, length = 64)
    private String cronExpression;

    @Column(name = "schedule_label", length = 64)
    private String scheduleLabel;

    @Column(name = "skill_id", length = 64)
    private String skillId;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;
}
