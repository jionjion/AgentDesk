package top.jionjion.agentdesk.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.jionjion.agentdesk.entity.FileRecord;

import java.util.List;

public interface FileRecordRepository extends JpaRepository<FileRecord, Long> {

    List<FileRecord> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    List<FileRecord> findByIdIn(List<Long> ids);
}
