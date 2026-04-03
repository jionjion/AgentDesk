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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(nullable = false)
    private String role;

    private String content;

    @Column(name = "tool_name")
    private String toolName;

    @Column(name = "tool_id")
    private String toolId;

    @Convert(converter = JsonMapConverter.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> arguments;

    private String result;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    public ChatMessage(String sessionId, String role, String content) {
        this.sessionId = sessionId;
        this.role = role;
        this.content = content;
        this.createdAt = System.currentTimeMillis();
    }
}
