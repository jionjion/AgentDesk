package top.jionjion.agentdesk.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import top.jionjion.agentdesk.converter.JsonMapConverter;

import java.util.Map;

/**
 * 对话消息实体
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "chat_messages", schema = "agent_desk")
public class ChatMessage {

    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 会话ID, 关联会话
     */
    @Column(name = "session_id", nullable = false)
    private String sessionId;

    /**
     * 消息角色: user / assistant / tool
     */
    @Column(nullable = false)
    private String role;

    /**
     * 消息文本内容
     */
    private String content;

    /**
     * 工具名称
     */
    @Column(name = "tool_name")
    private String toolName;

    /**
     * 工具调用ID
     */
    @Column(name = "tool_id")
    private String toolId;

    /**
     * 工具调用参数, JSON格式
     */
    @Convert(converter = JsonMapConverter.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> arguments;

    /**
     * 工具调用结果
     */
    private String result;

    /**
     * 创建时间, 毫秒时间戳
     */
    @Column(name = "created_at", nullable = false)
    private long createdAt;

    public ChatMessage(String sessionId, String role, String content) {
        this.sessionId = sessionId;
        this.role = role;
        this.content = content;
        this.createdAt = System.currentTimeMillis();
    }
}
