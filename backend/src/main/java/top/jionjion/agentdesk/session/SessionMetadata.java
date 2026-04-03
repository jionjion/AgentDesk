package top.jionjion.agentdesk.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 会话元数据
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
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
}
