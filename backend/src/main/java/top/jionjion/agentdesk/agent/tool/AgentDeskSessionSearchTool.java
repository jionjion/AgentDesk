package top.jionjion.agentdesk.agent.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import top.jionjion.agentdesk.agent.runtime.MemoryRuntimeContext;
import top.jionjion.agentdesk.agent.runtime.ProjectRuntimeContext;
import top.jionjion.agentdesk.entity.ChatMessage;
import top.jionjion.agentdesk.repository.ChatMessageRepository;
import top.jionjion.agentdesk.service.MemoryService;

import java.util.List;

/** Keeps session_search available after Harness built-in memory tools are disabled. */
public class AgentDeskSessionSearchTool {
    private final ChatMessageRepository messages;
    private final MemoryService memoryService;
    private final Long userId;

    public AgentDeskSessionSearchTool(ChatMessageRepository messages, MemoryService memoryService, Long userId) {
        this.messages = messages;
        this.memoryService = memoryService;
        this.userId = userId;
    }

    @Tool(name = "session_search", description = "搜索当前用户在当前项目范围内历史会话中的原始用户和助手消息。")
    public String search(@ToolParam(name = "query", description = "搜索关键词") String query,
                         ProjectRuntimeContext projectContext,
                         MemoryRuntimeContext memoryContext) {
        if (userId == null || query == null || query.isBlank()) return "没有可搜索的内容。";
        if (memoryContext != null && !memoryContext.enabled()) {
            return "本轮处于临时无记忆模式，不能搜索历史会话。";
        }
        if (memoryService != null && !memoryService.isEnabled(userId)) {
            return "用户已关闭长期记忆，不能搜索历史会话。";
        }
        String projectId = projectContext == null ? null : projectContext.projectId();
        List<ChatMessage> found = messages.searchByContentScoped(userId, query, projectId)
                .stream().limit(10).toList();
        if (found.isEmpty()) return "历史会话中没有匹配内容。";
        return found.stream().map(message -> "[" + message.getSessionId() + "][" + message.getRole() + "] " +
                abbreviate(message.getContent())).reduce((a, b) -> a + "\n" + b).orElse("");
    }

    private String abbreviate(String content) {
        if (content == null) return "";
        return content.length() <= 500 ? content : content.substring(0, 500) + "…";
    }
}
