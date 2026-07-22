package top.jionjion.agentdesk.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Immutable audit record for manual or automatic memory changes. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "memory_revisions", schema = "agent_desk")
public class MemoryRevision {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "memory_id", nullable = false)
    private Long memoryId;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "old_content", columnDefinition = "text")
    private String oldContent;
    @Column(name = "new_content", columnDefinition = "text")
    private String newContent;
    @Column(nullable = false, length = 32)
    private String reason;
    @Column(nullable = false, length = 32)
    private String actor;
    @Column(name = "created_at", nullable = false)
    private long createdAt;
}
