package top.jionjion.agentdesk.service.chat;

import org.junit.jupiter.api.Test;
import top.jionjion.agentdesk.entity.ChatMessage;
import top.jionjion.agentdesk.repository.ChatMessageRepository;
import top.jionjion.agentdesk.service.SessionService;
import top.jionjion.agentdesk.service.TitleGenerationService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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

    private static ChatMessage message(Long id, String role, String content) {
        ChatMessage message = new ChatMessage("session-1", role, content);
        message.setId(id);
        return message;
    }
}
