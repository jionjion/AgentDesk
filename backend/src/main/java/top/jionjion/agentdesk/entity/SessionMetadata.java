package top.jionjion.agentdesk.entity;

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
 *
 * @author Jion
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sessions", schema = "agent_desk")
public class SessionMetadata {

    /**
     * 会话ID, 主键
     */
    @Id
    private String id;

    /**
     * 会话标题
     */
    private String title;

    /**
     * 创建时间, 毫秒时间戳
     */
    @Column(name = "created_at", nullable = false)
    private long createdAt;

    /**
     * 最后使用时间, 毫秒时间戳
     */
    @Column(name = "last_used_at", nullable = false)
    private long lastUsedAt;

    /**
     * 所属用户ID
     */
    @Column(name = "user_id")
    private Long userId;

    /**
     * 可选关联项目ID
     */
    @Column(name = "project_id", length = 32)
    private String projectId;

    /** Server-persisted default for new turns in this session. */
    @Column(name = "memory_mode", nullable = false, length = 16)
    private String memoryMode = "NORMAL";
}
