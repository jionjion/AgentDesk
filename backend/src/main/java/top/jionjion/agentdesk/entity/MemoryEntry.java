package top.jionjion.agentdesk.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Canonical, user-owned long-term memory. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "memory_entries", schema = "agent_desk")
public class MemoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "scope_type", nullable = false, length = 16)
    private String scopeType = "USER";

    @Column(name = "scope_id", length = 64)
    private String scopeId;

    @Column(nullable = false, length = 32)
    private String category = "OTHER";

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "canonical_content", nullable = false, columnDefinition = "text")
    private String canonicalContent;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(nullable = false, length = 16)
    private String sensitivity = "NORMAL";

    @Column(name = "write_policy", nullable = false, length = 16)
    private String writePolicy = "EXPLICIT";

    @Column(name = "subject_key", length = 128)
    private String subjectKey;

    @Column(name = "supersedes_id")
    private Long supersedesId;

    @Column(nullable = false)
    private double confidence = 1.0d;

    @Column(nullable = false)
    private double importance = 0.5d;

    @Column(nullable = false)
    private boolean pinned;

    @Column(name = "provider_name", length = 32)
    private String providerName;

    @Column(name = "provider_ref", length = 128)
    private String providerRef;

    @Column(name = "provider_sync_pending", nullable = false)
    private boolean providerSyncPending;

    @Column(name = "valid_until")
    private Long validUntil;

    @Column(name = "valid_from")
    private Long validFrom;

    @Column(name = "last_recalled_at")
    private Long lastRecalledAt;

    @Column(name = "recall_count", nullable = false)
    private long recallCount;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;

    @Column(name = "deleted_at")
    private Long deletedAt;
}
