package top.jionjion.agentdesk.session;

/**
 * 会话元数据
 */
public class SessionMetadata {

    private String id;
    private String title;
    private long createdAt;
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
