package top.jionjion.agentdesk.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 文件上传记录
 *
 * @author Jion
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "files", schema = "agent_desk")
public class FileRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "oss_key", nullable = false)
    private String ossKey;

    @Column(name = "content_type")
    private String contentType;

    @Column(nullable = false)
    private long size;

    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    public FileRecord(String originalName, String ossKey, String contentType, long size, String sessionId) {
        this.originalName = originalName;
        this.ossKey = ossKey;
        this.contentType = contentType;
        this.size = size;
        this.sessionId = sessionId;
        this.createdAt = System.currentTimeMillis();
    }
}
