package top.jionjion.agentdesk.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Minimal recall telemetry; deliberately stores no query or memory body. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "memory_usage_events", schema = "agent_desk")
public class MemoryUsageEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "memory_id", nullable = false)
    private Long memoryId;
    @Column(name = "project_id", length = 64)
    private String projectId;
    @Column(nullable = false, length = 32)
    private String reason;
    @Column(nullable = false)
    private double score;
    @Column(name = "elapsed_ms", nullable = false)
    private long elapsedMs;
    @Column(name = "created_at", nullable = false)
    private long createdAt;
}
