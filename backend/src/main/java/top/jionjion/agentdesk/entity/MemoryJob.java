package top.jionjion.agentdesk.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Durable extraction job created after a successful chat turn. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "memory_jobs", schema = "agent_desk")
public class MemoryJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Version
    @Column(nullable = false)
    private long version;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;
    @Column(name = "project_id", length = 64)
    private String projectId;
    @Column(name = "user_message_id", nullable = false)
    private Long userMessageId;
    /** Legacy compatibility only; new jobs read the canonical chat message by ID. */
    @Column(name = "user_message", columnDefinition = "text")
    private String userMessage;
    @Column(nullable = false, length = 16)
    private String status = "PENDING";
    @Column(nullable = false)
    private int attempts;
    @Column(name = "next_attempt_at", nullable = false)
    private long nextAttemptAt;
    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;
    @Column(name = "created_at", nullable = false)
    private long createdAt;
    @Column(name = "updated_at", nullable = false)
    private long updatedAt;
}
