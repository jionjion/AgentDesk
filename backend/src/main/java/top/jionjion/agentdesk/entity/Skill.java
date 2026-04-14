package top.jionjion.agentdesk.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import top.jionjion.agentdesk.converter.StringListConverter;

import java.util.List;

/**
 * 技能定义实体
 *
 * @author Jion
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "skills", schema = "agent_desk")
public class Skill {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 512)
    private String description;

    @Column(nullable = false)
    private String author;

    @Column(nullable = false)
    private String version;

    @Column(nullable = false)
    private String category;

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<String> tags;

    private String icon;

    @Column(name = "bg_color")
    private String bgColor;

    @Column(name = "sys_prompt", columnDefinition = "text", nullable = false)
    private String sysPrompt;

    @Column(name = "max_iters", nullable = false)
    private int maxIters;

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<String> tools;

    @Column(nullable = false)
    private boolean builtin;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;
}
