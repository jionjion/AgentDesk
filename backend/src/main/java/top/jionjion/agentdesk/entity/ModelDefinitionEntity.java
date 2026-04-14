package top.jionjion.agentdesk.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import top.jionjion.agentdesk.converter.StringListConverter;

import java.util.List;

/**
 * 模型定义实体
 *
 * @author Jion
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "model_definitions", schema = "agent_desk")
public class ModelDefinitionEntity {

    /**
     * 模型ID, 即 DashScope API 的 model 参数, 如 "qwen3.6-plus"
     */
    @Id
    private String id;

    /**
     * 前端展示名称
     */
    @Column(name = "display_name", nullable = false)
    private String displayName;

    /**
     * 分组名称, 如 "旗舰模型"
     */
    @Column(name = "group_name", nullable = false)
    private String groupName;

    /**
     * 支持的输入模态, 如 ["text", "image", "video"]
     */
    @Convert(converter = StringListConverter.class)
    @Column(name = "input_modalities", columnDefinition = "jsonb", nullable = false)
    private List<String> inputModalities;

    /**
     * 是否支持深度思考/推理
     */
    @Column(name = "supports_reasoning", nullable = false)
    private boolean supportsReasoning;

    /**
     * 最大上下文窗口 (token)
     */
    @Column(name = "max_context_window", nullable = false)
    private int maxContextWindow;

    /**
     * 最大输出 token 数
     */
    @Column(name = "max_output_tokens", nullable = false)
    private int maxOutputTokens;

    /**
     * 简要说明
     */
    private String description;

    /**
     * 排序权重, 越小越靠前
     */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /**
     * 是否启用
     */
    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;
}
