package top.jionjion.agentdesk.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 定时任务执行记录实体
 *
 * @author Jion
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "scheduled_task_logs", schema = "agent_desk")
public class ScheduledTaskLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(columnDefinition = "text")
    private String result;

    @Column(nullable = false)
    private long duration;

    @Column(name = "started_at", nullable = false)
    private long startedAt;

    @Column(name = "ended_at")
    private Long endedAt;
}
