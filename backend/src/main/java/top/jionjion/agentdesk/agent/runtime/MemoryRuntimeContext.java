package top.jionjion.agentdesk.agent.runtime;

/** Per-call gate used by memory tools. Temporary no-memory mode sets enabled=false. */
public record MemoryRuntimeContext(boolean enabled) {
}
