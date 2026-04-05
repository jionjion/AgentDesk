package top.jionjion.agentdesk.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import top.jionjion.agentdesk.entity.ChatMessage;

import java.util.List;

/**
 * 对话消息 — JPA 持久层
 */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    @Transactional
    void deleteBySessionId(String sessionId);
}
