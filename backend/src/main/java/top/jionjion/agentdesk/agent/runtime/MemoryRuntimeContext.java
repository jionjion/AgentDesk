package top.jionjion.agentdesk.agent.runtime;

/**
 * Per-call gate used by memory tools. Temporary no-memory mode sets enabled=false.
 * sessionId 与 userMessageId 用于显式保存时落真实 CHAT 来源, 可为空 (缺失时回退 MANUAL 来源)。
 */
public record MemoryRuntimeContext(boolean enabled, String sessionId, Long userMessageId) {

    public MemoryRuntimeContext(boolean enabled) {
        this(enabled, null, null);
    }
}
