package top.jionjion.agentdesk.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import top.jionjion.agentdesk.entity.ChatMessage;

import java.util.List;

/**
 * 对话消息 — JPA 持久层
 *
 * @author Jion
 */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * 根据会话ID查询消息列表, 按创建时间升序排列
     *
     * @param sessionId 会话ID
     * @return 消息列表
     */
    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    /**
     * 统计指定会话的消息数量
     *
     * @param sessionId 会话ID
     * @return 消息数量
     */
    long countBySessionId(String sessionId);

    /**
     * 删除指定会话的所有消息
     *
     * @param sessionId 会话ID
     */
    @Transactional(rollbackFor = Exception.class)
    void deleteBySessionId(String sessionId);

    /**
     * 全文搜索: 在当前用户的所有会话中搜索消息内容
     *
     * @param userId  用户ID
     * @param keyword 搜索关键词
     * @return 匹配的消息列表
     */
    @Query("SELECT m FROM ChatMessage m WHERE m.sessionId IN " +
           "(SELECT s.id FROM SessionMetadata s WHERE s.userId = :userId) " +
           "AND m.content IS NOT NULL AND LOWER(m.content) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "AND m.role IN ('user', 'assistant') " +
           "ORDER BY m.createdAt DESC")
    List<ChatMessage> searchByContent(@Param("userId") Long userId, @Param("keyword") String keyword);
}
