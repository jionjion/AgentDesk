package top.jionjion.agentdesk.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 用户实体
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "users", schema = "agent_desk")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(nullable = false, length = 256)
    private String password;

    @Column(length = 128)
    private String nickname;

    @Column(length = 512)
    private String avatar;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;

    public User(String username, String password, String nickname) {
        this.username = username;
        this.password = password;
        this.nickname = nickname;
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.updatedAt = now;
    }
}
