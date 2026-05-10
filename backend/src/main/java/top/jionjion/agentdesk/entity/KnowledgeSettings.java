package top.jionjion.agentdesk.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 用户知识库检索配置
 *
 * @author Jion
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "knowledge_settings", schema = "agent_desk")
public class KnowledgeSettings {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "top_k", nullable = false)
    private int topK = 5;

    @Column(name = "score_threshold", nullable = false)
    private double scoreThreshold = 0.8;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;

    public KnowledgeSettings(Long userId) {
        this.userId = userId;
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.updatedAt = now;
    }
}
