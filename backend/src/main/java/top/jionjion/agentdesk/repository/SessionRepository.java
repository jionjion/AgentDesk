package top.jionjion.agentdesk.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import top.jionjion.agentdesk.session.SessionMetadata;

import java.util.List;
import java.util.Optional;

/**
 * 会话元数据 — JPA 持久层
 *
 * @author Jion
 */
public interface SessionRepository extends JpaRepository<SessionMetadata, String> {

    /**
     * 根据用户ID查询会话列表
     *
     * @param userId 用户ID
     * @param sort   排序规则
     * @return 会话元数据列表
     */
    List<SessionMetadata> findByUserId(Long userId, Sort sort);

    /**
     * 根据会话ID和用户ID查询会话
     *
     * @param id     会话ID
     * @param userId 用户ID
     * @return 会话元数据
     */
    Optional<SessionMetadata> findByIdAndUserId(String id, Long userId);
}
