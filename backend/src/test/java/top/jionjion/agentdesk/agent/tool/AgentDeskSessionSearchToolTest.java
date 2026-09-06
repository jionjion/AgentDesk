package top.jionjion.agentdesk.agent.tool;

import org.junit.jupiter.api.Test;
import top.jionjion.agentdesk.agent.runtime.MemoryRuntimeContext;
import top.jionjion.agentdesk.agent.runtime.ProjectRuntimeContext;
import top.jionjion.agentdesk.entity.ChatMessage;
import top.jionjion.agentdesk.repository.ChatMessageRepository;
import top.jionjion.agentdesk.service.MemoryService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentDeskSessionSearchToolTest {

    private final ChatMessageRepository messages = mock(ChatMessageRepository.class);
    private final MemoryService memoryService = mock(MemoryService.class);
    private final AgentDeskSessionSearchTool tool =
            new AgentDeskSessionSearchTool(messages, memoryService, 1L);

    @Test
    void temporaryNoMemoryTurnCannotSearchHistory() {
        String result = tool.search("周报", projectContext("p1"),
                new MemoryRuntimeContext(false, "s1", 42L));

        assertEquals("本轮处于临时无记忆模式，不能搜索历史会话。", result);
        verifyNoInteractions(messages);
    }

    @Test
    void globallyDisabledMemoryAlsoBlocksHistorySearch() {
        when(memoryService.isEnabled(1L)).thenReturn(false);

        String result = tool.search("周报", projectContext("p1"),
                new MemoryRuntimeContext(true, "s1", 42L));

        assertEquals("用户已关闭长期记忆，不能搜索历史会话。", result);
        verifyNoInteractions(messages);
    }

    @Test
    void searchIsScopedToCurrentProjectAndExcludesNoMemoryTurns() {
        when(memoryService.isEnabled(1L)).thenReturn(true);
        ChatMessage hit = new ChatMessage("s1", "user", "本周周报重点");
        hit.setId(5L);
        when(messages.searchByContentScoped(1L, "周报", "p1")).thenReturn(List.of(hit));

        String result = tool.search("周报", projectContext("p1"),
                new MemoryRuntimeContext(true, "s1", 42L));

        assertTrue(result.contains("本周周报重点"));
        verify(messages).searchByContentScoped(1L, "周报", "p1");
    }

    @Test
    void unboundSessionOnlySearchesUnboundHistory() {
        when(memoryService.isEnabled(1L)).thenReturn(true);
        when(messages.searchByContentScoped(1L, "周报", null)).thenReturn(List.of());

        String result = tool.search("周报", null, new MemoryRuntimeContext(true, "s1", 42L));

        assertEquals("历史会话中没有匹配内容。", result);
        verify(messages).searchByContentScoped(1L, "周报", null);
    }

    private ProjectRuntimeContext projectContext(String projectId) {
        return new ProjectRuntimeContext(projectId, "项目", null, null, null, null, null, null, null, false);
    }
}
