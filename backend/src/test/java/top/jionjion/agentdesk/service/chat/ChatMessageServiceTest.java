package top.jionjion.agentdesk.service.chat;

import org.junit.jupiter.api.Test;
import top.jionjion.agentdesk.entity.ChatMessage;
import top.jionjion.agentdesk.repository.ChatMessageRepository;
import top.jionjion.agentdesk.service.SessionService;
import top.jionjion.agentdesk.service.TitleGenerationService;
import top.jionjion.agentdesk.service.MemoryService;
import top.jionjion.agentdesk.service.memory.MemoryJobService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.mockito.ArgumentMatchers.argThat;

class ChatMessageServiceTest {

    private final ChatMessageRepository repository = mock(ChatMessageRepository.class);
    private final ChatMessageService service = new ChatMessageService(
            repository, mock(SessionService.class), mock(TitleGenerationService.class));

    @Test
    void regenerationFindsTheUserTurnImmediatelyBeforeSelectedAssistantBranch() {
        ChatMessage firstUser = message(1L, "user", "第一问");
        ChatMessage firstAssistant = message(2L, "assistant", "第一答");
        ChatMessage targetUser = message(3L, "user", "重新回答这一问");
        ChatMessage targetAssistant = message(4L, "assistant", "旧回答");

        ChatMessage result = service.findTriggeringUserMessageEntity(
                4L, List.of(firstUser, firstAssistant, targetUser, targetAssistant));

        assertEquals(3L, result.getId());
        assertEquals("重新回答这一问", result.getContent());
    }

    @Test
    void regenerationDeletesSelectedReplyAndEveryLaterMessage() {
        service.deleteBranchFrom("session-1", 42L);

        verify(repository).deleteBySessionIdAndIdGreaterThanEqual("session-1", 42L);
    }

    @Test
    void userTurnPersistsNoMemoryPolicyForRegeneration() {
        service.saveUserMessage("session-1", "敏感内容", List.of(), "NO_MEMORY");

        verify(repository).save(argThat(message -> "NO_MEMORY".equals(message.getMemoryMode())
                && "user".equals(message.getRole())));
    }

    @Test
    void assistantReplyAndMemoryOutboxAreCommittedThroughOneServiceBoundary() {
        MemoryJobService jobs = mock(MemoryJobService.class);
        ChatMessageService transactionalService = new ChatMessageService(repository,
                mock(SessionService.class), mock(TitleGenerationService.class),
                mock(MemoryService.class), jobs);
        when(repository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage saved = invocation.getArgument(0);
            saved.setId(9L);
            return saved;
        });

        Long id = transactionalService.saveAssistantReplyAndQueueMemory(
                "session-1", "回复", null, true, 1L, "p1", 7L, "原始问题", "NORMAL");

        assertEquals(9L, id);
        verify(jobs).enqueue(1L, "session-1", "p1", 7L, "原始问题");
    }

    @Test
    void assistantReplyRecordsTheTurnMemoryModeForHistorySearchFiltering() {
        when(repository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        service.saveAssistantReply("session-1", "临时回复", null, "NO_MEMORY");

        verify(repository).save(argThat(message -> "NO_MEMORY".equals(message.getMemoryMode())
                && "assistant".equals(message.getRole())));
    }

    private static ChatMessage message(Long id, String role, String content) {
        ChatMessage message = new ChatMessage("session-1", role, content);
        message.setId(id);
        return message;
    }
}
