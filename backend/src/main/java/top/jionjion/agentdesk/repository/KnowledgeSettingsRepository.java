package top.jionjion.agentdesk.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.jionjion.agentdesk.entity.KnowledgeSettings;

/**
 * 知识库检索设置 — JPA 持久层
 *
 * @author Jion
 */
public interface KnowledgeSettingsRepository extends JpaRepository<KnowledgeSettings, Long> {
}
