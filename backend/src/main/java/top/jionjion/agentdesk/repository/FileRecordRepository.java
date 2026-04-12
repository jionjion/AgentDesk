package top.jionjion.agentdesk.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.jionjion.agentdesk.entity.FileRecord;

import java.util.List;
import java.util.Optional;

/**
 * 文件记录 — JPA 持久层
 *
 * @author Jion
 */
public interface FileRecordRepository extends JpaRepository<FileRecord, Long> {

    /**
     * 根据会话ID查询文件记录, 按创建时间升序排列
     *
     * @param sessionId 会话ID
     * @return 文件记录列表
     */
    List<FileRecord> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    /**
     * 根据ID列表批量查询文件记录
     *
     * @param ids 文件ID列表
     * @return 文件记录列表
     */
    List<FileRecord> findByIdIn(List<Long> ids);

    /**
     * 根据文件ID和用户ID查询文件记录
     *
     * @param id     文件ID
     * @param userId 用户ID
     * @return 文件记录
     */
    Optional<FileRecord> findByIdAndUserId(Long id, Long userId);

    /**
     * 根据会话ID和用户ID查询文件记录, 按创建时间升序排列
     *
     * @param sessionId 会话ID
     * @param userId    用户ID
     * @return 文件记录列表
     */
    List<FileRecord> findBySessionIdAndUserIdOrderByCreatedAtAsc(String sessionId, Long userId);
}
