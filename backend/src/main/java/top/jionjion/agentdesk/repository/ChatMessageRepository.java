package top.jionjion.agentdesk.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import top.jionjion.agentdesk.entity.ChatMessage;

import java.util.List;

/**
 * 对话消息 — JPA 持久层
 */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    long countBySessionId(String sessionId);

    @Transactional
    void deleteBySessionId(String sessionId);

    /**
     * 全文搜索: 在当前用户的所有会话中搜索消息内容
     */
    @Query("SELECT m FROM ChatMessage m WHERE m.sessionId IN " +
           "(SELECT s.id FROM SessionMetadata s WHERE s.userId = :userId) " +
           "AND m.content IS NOT NULL AND LOWER(m.content) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "AND m.role IN ('user', 'assistant') " +
           "ORDER BY m.createdAt DESC")
    List<ChatMessage> searchByContent(@Param("userId") Long userId, @Param("keyword") String keyword);
}
