package top.jionjion.agentdesk.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import top.jionjion.agentdesk.session.SessionMetadata;

import java.util.List;
import java.util.Optional;

/**
 * 会话元数据 — JPA 持久层
 */
public interface SessionRepository extends JpaRepository<SessionMetadata, String> {

    List<SessionMetadata> findByUserId(Long userId, Sort sort);

    Optional<SessionMetadata> findByIdAndUserId(String id, Long userId);
}
