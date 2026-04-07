package top.jionjion.agentdesk.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.jionjion.agentdesk.entity.FileRecord;

import java.util.List;
import java.util.Optional;

public interface FileRecordRepository extends JpaRepository<FileRecord, Long> {

    List<FileRecord> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    List<FileRecord> findByIdIn(List<Long> ids);

    Optional<FileRecord> findByIdAndUserId(Long id, Long userId);

    List<FileRecord> findBySessionIdAndUserIdOrderByCreatedAtAsc(String sessionId, Long userId);
}
