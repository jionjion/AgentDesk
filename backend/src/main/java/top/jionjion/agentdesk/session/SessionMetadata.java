package top.jionjion.agentdesk.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 会话元数据
 */
@Entity
@Table(name = "sessions", schema = "agent_desk")
public class SessionMetadata {

    @Id
    private String id;

    private String title;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    @Column(name = "last_used_at", nullable = false)
    private long lastUsedAt;

    public SessionMetadata() {
    }

    public SessionMetadata(String id, String title, long createdAt, long lastUsedAt) {
        this.id = id;
        this.title = title;
        this.createdAt = createdAt;
        this.lastUsedAt = lastUsedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(long lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }
}
