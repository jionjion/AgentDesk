package top.jionjion.agentdesk.agent.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import top.jionjion.agentdesk.agent.runtime.MemoryRuntimeContext;
import top.jionjion.agentdesk.agent.runtime.ProjectRuntimeContext;
import top.jionjion.agentdesk.dto.memory.MemoryItemDto;
import top.jionjion.agentdesk.dto.memory.MemoryRecallResult;
import top.jionjion.agentdesk.service.MemoryService;

import java.util.List;

/** Agent-facing tools backed by the same canonical store as the settings UI and chat recall. */
public class AgentDeskMemoryTools {
    private final MemoryService memoryService;
    private final Long userId;

    public AgentDeskMemoryTools(MemoryService memoryService, Long userId) {
        this.memoryService = memoryService;
        this.userId = userId;
    }

    @Tool(name = "memory_search", description = "搜索当前用户及当前项目的长期记忆。仅在确有必要时使用。")
    public String search(
            @ToolParam(name = "query", description = "要查找的事实、偏好或项目背景") String query,
            ProjectRuntimeContext projectContext,
            MemoryRuntimeContext memoryContext) {
        String blocked = precheck(memoryContext);
        if (blocked != null) return blocked;
        String projectId = projectContext == null ? null : projectContext.projectId();
        MemoryRecallResult result = memoryService.recall(userId, projectId, query, "NORMAL");
        if (result.items().isEmpty()) return "未找到相关长期记忆。";
        return format(result.items());
    }

    @Tool(name = "memory_get", description = "按 ID 读取当前 USER 或当前 PROJECT 范围内的长期记忆。")
    public String get(
            @ToolParam(name = "memory_id", description = "记忆 ID") String memoryId,
            ProjectRuntimeContext projectContext,
            MemoryRuntimeContext memoryContext) {
        String blocked = precheck(memoryContext);
        if (blocked != null) return blocked;
        String projectId = projectContext == null ? null : projectContext.projectId();
        return memoryService.findAccessibleMemory(userId, memoryId, projectId)
                .map(item -> format(List.of(item))).orElse("记忆不存在或不在当前记忆范围。 ");
    }

    @Tool(name = "memory_save", description = "保存用户明确要求记住的稳定事实或偏好。不要保存推测、临时内容、检索文本或助手生成内容。")
    public String save(
            @ToolParam(name = "content", description = "简洁、独立、可复用的记忆内容") String content,
            @ToolParam(name = "scope", description = "USER 或 PROJECT；默认当前项目存在时为 PROJECT", required = false) String scope,
            @ToolParam(name = "category", description = "PREFERENCE、IDENTITY、SCHEDULE、PROJECT_FACT 或 OTHER", required = false) String category,
            ProjectRuntimeContext projectContext,
            MemoryRuntimeContext memoryContext) {
        String blocked = precheck(memoryContext);
        if (blocked != null) return blocked;
        String projectId = projectContext == null ? null : projectContext.projectId();
        String resolvedScope = scope == null || scope.isBlank()
                ? (projectId == null ? "USER" : "PROJECT") : scope;
        String resolvedScopeId = "PROJECT".equalsIgnoreCase(resolvedScope) ? projectId : null;
        boolean hasChatSource = memoryContext != null && memoryContext.sessionId() != null
                && memoryContext.userMessageId() != null;
        // 优先记录真实的会话/用户消息来源, 使会话删除与分支重生成可以级联失效
        MemoryItemDto saved = hasChatSource
                ? memoryService.addExplicitChatMemory(userId, content, resolvedScope, resolvedScopeId,
                        category, memoryContext.sessionId(), memoryContext.userMessageId())
                : memoryService.addMemory(userId, content, resolvedScope, resolvedScopeId, category);
        return "已保存记忆，ID=" + saved.id() + "，范围=" + saved.scopeType();
    }

    @Tool(name = "memory_forget", description = "按 ID 删除一条属于当前用户的长期记忆。仅在用户明确要求遗忘时使用。")
    public String forget(
            @ToolParam(name = "memory_id", description = "要遗忘的记忆 ID") String memoryId,
            ProjectRuntimeContext projectContext,
            MemoryRuntimeContext memoryContext) {
        String blocked = precheck(memoryContext);
        if (blocked != null) return blocked;
        String projectId = projectContext == null ? null : projectContext.projectId();
        if (memoryService.findAccessibleMemory(userId, memoryId, projectId).isEmpty()) {
            return "记忆不存在或不在当前记忆范围。";
        }
        memoryService.deleteMemory(userId, memoryId);
        return "已遗忘记忆 " + memoryId;
    }

    private String precheck(MemoryRuntimeContext context) {
        if (userId == null) return "当前没有已认证用户，不能使用长期记忆。";
        if (context != null && !context.enabled()) return "本轮处于临时无记忆模式，不能读取或写入长期记忆。";
        if (!memoryService.isEnabled(userId)) return "用户已关闭长期记忆。";
        return null;
    }

    private String format(List<MemoryItemDto> items) {
        return items.stream().map(item -> "[" + item.id() + "][" + item.scopeType() + "][" +
                item.category() + "] " + item.memory()).reduce((a, b) -> a + "\n" + b).orElse("");
    }
}
