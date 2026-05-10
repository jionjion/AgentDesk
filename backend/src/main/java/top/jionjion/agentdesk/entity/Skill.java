package top.jionjion.agentdesk.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import top.jionjion.agentdesk.converter.StringListConverter;

import java.util.List;

/**
 * 技能定义实体
 * <p>
 * 支持两种技能类型:
 * - "prompt": 传统提示词驱动的技能（向后兼容）
 * - "package": 脚本化技能包，存储在文件系统
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

    /**
     * 技能类型: "prompt"(提示词驱动) 或 "package"(脚本化技能包)
     */
    @Column(name = "skill_type", length = 16)
    private String skillType;

    /**
     * 技能包安装路径（仅 package 类型有值）
     */
    @Column(name = "install_path", length = 512)
    private String installPath;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;
}
