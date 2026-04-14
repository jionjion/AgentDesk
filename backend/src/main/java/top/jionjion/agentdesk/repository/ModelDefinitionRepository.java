package top.jionjion.agentdesk.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.jionjion.agentdesk.entity.ModelDefinitionEntity;

import java.util.List;

/**
 * 模型定义 — JPA 持久层
 *
 * @author Jion
 */
public interface ModelDefinitionRepository extends JpaRepository<ModelDefinitionEntity, String> {

    /**
     * 查询所有启用的模型, 按排序权重升序
     */
    List<ModelDefinitionEntity> findByEnabledTrueOrderBySortOrderAsc();
}
