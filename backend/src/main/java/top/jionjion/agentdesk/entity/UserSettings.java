package top.jionjion.agentdesk.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 用户设置实体
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "user_settings", schema = "agent_desk")
public class UserSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(columnDefinition = "jsonb", nullable = false)
    private String settings;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;

    public UserSettings(Long userId, String settings) {
        this.userId = userId;
        this.settings = settings;
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.updatedAt = now;
    }
}
