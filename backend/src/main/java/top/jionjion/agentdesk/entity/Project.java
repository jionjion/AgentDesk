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
 * 项目实体。逻辑项目, 可绑定多个会话。
 *
 * <p>项目在具体设备上的本地路径与解释器配置保存在 PC 端 ProjectLocation 中, 不入服务器数据库。
 *
 * @author Jion
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "projects", schema = "agent_desk")
public class Project {

    /**
     * 项目ID, 16位十六进制字符串
     */
    @Id
    private String id;

    /**
     * 所属用户ID
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 项目名称
     */
    @Column(nullable = false, length = 256)
    private String name;

    /**
     * 项目说明
     */
    @Column(columnDefinition = "text")
    private String description;

    /**
     * 项目级指令, 注入 Agent 上下文
     */
    @Column(columnDefinition = "text")
    private String instructions;

    /**
     * 创建时间, 毫秒时间戳
     */
    @Column(name = "created_at", nullable = false)
    private long createdAt;

    /**
     * 更新时间, 毫秒时间戳
     */
    @Column(name = "updated_at", nullable = false)
    private long updatedAt;
}
