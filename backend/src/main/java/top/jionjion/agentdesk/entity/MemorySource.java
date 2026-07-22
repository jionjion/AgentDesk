package top.jionjion.agentdesk.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Evidence that explains where a memory came from. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "memory_sources", schema = "agent_desk")
public class MemorySource {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "memory_id", nullable = false)
    private Long memoryId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "source_type", nullable = false, length = 24)
    private String sourceType;

    @Column(name = "source_id", length = 128)
    private String sourceId;

    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Column(name = "project_id", length = 64)
    private String projectId;

    @Column(name = "evidence_excerpt", columnDefinition = "text")
    private String evidenceExcerpt;

    @Column(name = "trust_level", nullable = false, length = 16)
    private String trustLevel = "USER";

    @Column(name = "created_at", nullable = false)
    private long createdAt;
}
