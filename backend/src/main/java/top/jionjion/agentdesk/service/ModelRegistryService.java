package top.jionjion.agentdesk.service;

import org.springframework.stereotype.Service;
import top.jionjion.agentdesk.dto.ModelDefinition;
import top.jionjion.agentdesk.entity.ModelDefinitionEntity;
import top.jionjion.agentdesk.repository.ModelDefinitionRepository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 模型注册表: 从数据库读取可用模型列表
 *
 * @author Jion
 */
@Service
public class ModelRegistryService {

    private final ModelDefinitionRepository modelDefinitionRepository;

    public ModelRegistryService(ModelDefinitionRepository modelDefinitionRepository) {
        this.modelDefinitionRepository = modelDefinitionRepository;
    }

    /** 返回全部启用的模型（按 sort_order 排序） */
    public List<ModelDefinition> listModels() {
        return modelDefinitionRepository.findByEnabledTrueOrderBySortOrderAsc()
                .stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * 按 group 分组返回, 保持排序顺序
     */
    public Map<String, List<ModelDefinition>> listGrouped() {
        return modelDefinitionRepository.findByEnabledTrueOrderBySortOrderAsc()
                .stream()
                .map(this::toDto)
                .collect(Collectors.groupingBy(ModelDefinition::group, LinkedHashMap::new, Collectors.toList()));
    }

    /** 根据 ID 查找模型定义 */
    public Optional<ModelDefinition> findById(String id) {
        return modelDefinitionRepository.findById(id)
                .filter(ModelDefinitionEntity::isEnabled)
                .map(this::toDto);
    }

    /** 检查模型 ID 是否合法（存在且启用） */
    public boolean isValidModelId(String id) {
        return modelDefinitionRepository.findById(id)
                .map(ModelDefinitionEntity::isEnabled)
                .orElse(false);
    }

    /** Entity → DTO 转换 */
    private ModelDefinition toDto(ModelDefinitionEntity entity) {
        return new ModelDefinition(
                entity.getId(),
                entity.getDisplayName(),
                entity.getGroupName(),
                entity.getInputModalities(),
                entity.isSupportsReasoning(),
                entity.getMaxContextWindow(),
                entity.getMaxOutputTokens(),
                entity.getDescription()
        );
    }
}
