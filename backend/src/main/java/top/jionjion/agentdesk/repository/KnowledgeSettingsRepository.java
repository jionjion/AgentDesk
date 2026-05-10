package top.jionjion.agentdesk.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.jionjion.agentdesk.entity.KnowledgeSettings;

public interface KnowledgeSettingsRepository extends JpaRepository<KnowledgeSettings, Long> {
}
