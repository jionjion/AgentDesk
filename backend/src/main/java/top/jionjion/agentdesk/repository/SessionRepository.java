package top.jionjion.agentdesk.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.jionjion.agentdesk.session.SessionMetadata;

/**
 * 会话元数据 — JPA 持久层
 */
public interface SessionRepository extends JpaRepository<SessionMetadata, String> {
}
