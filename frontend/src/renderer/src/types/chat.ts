/**
 * 聊天相关类型定义
 */

/** 会话 */
export interface ChatSession {
    id: string
    title: string
    createdAt: number
    lastUsedAt: number
    /** 可选关联项目ID */
    projectId?: string | null
    /** 可选关联项目名称 */
    projectName?: string | null
    /** 服务端持久化的新轮次默认记忆模式 */
    memoryMode: 'NORMAL' | 'NO_MEMORY'
}

/** 附件信息 */
export interface Attachment {
    id: number
    name: string
    size: number
    contentType: string
    url?: string
}

/** 用户消息 */
export interface UserMessage {
    id: string
    role: 'user'
    content: string
    timestamp: number
    attachments?: Attachment[]
    memoryMode?: 'NORMAL' | 'NO_MEMORY'
}

/** 助手消息 */
export interface AssistantMessage {
    id: string
    role: 'assistant'
    content: string
    timestamp: number
    isStreaming: boolean
    knowledgeRefs?: {documentName: string; score: number; chunkIndex: number}[]
    memoryRefs?: MemoryRecallInfo
}

export interface MemoryRecallReference {
    id: string
    scopeType: 'USER' | 'PROJECT'
    scopeId?: string | null
    category: string
    reason?: string | null
    score?: number
}

export interface MemoryRecallInfo {
    status: 'USED' | 'EMPTY' | 'DISABLED' | 'DEGRADED'
    count?: number
    elapsedMs: number
    degradedReason?: string | null
    references: MemoryRecallReference[]
}

/** 工具调用消息 */
export interface ToolCallMessage {
    id: string
    role: 'tool_call'
    toolName: string
    toolId: string
    arguments: Record<string, unknown>
    result?: string
    status: 'calling' | 'done' | 'error'
}

/** 思考过程消息 */
export interface ThinkingMessage {
    id: string
    role: 'thinking'
    content: string
    isCollapsed: boolean
}

/** 规划消息 */
export interface PlanMessage {
    id: string
    role: 'plan'
    toolName: string
    arguments: Record<string, unknown>
    result?: string
}

/** 命令审批消息 */
export interface CommandApprovalMessage {
    id: string
    role: 'command_approval'
    requestId: string
    sessionId: string
    command: string
    workingDir: string
    riskLevel: 'LOW' | 'HIGH'
    timeoutMs: number
    receivedAt: number
    status: 'pending' | 'approved' | 'rejected' | 'timeout' | 'cancelled'
    timestamp: number
}

/** 子任务 */
export interface Subtask {
    name: string
    state: 'todo' | 'in_progress' | 'done' | 'abandoned'
}

/** 子智能体面板内的工具调用 */
export interface SubagentToolCall {
    toolId: string
    toolName: string
    arguments: Record<string, unknown>
    result?: string
    status: 'calling' | 'done'
}

/** 子智能体执行面板消息（按 source 聚合 subagent_event 事件流） */
export interface SubagentMessage {
    id: string
    role: 'subagent'
    /** 分组键（完整路径，如 main/researcher） */
    source: string
    /** 末段名，如 researcher */
    agentId: string
    /** start 事件携带的专家显示名 */
    name: string
    /** 子智能体的正文输出（text_chunk 聚合） */
    content: string
    /** 思考输出（thinking_chunk 聚合） */
    thinking: string
    /** 工具调用列表 */
    toolCalls: SubagentToolCall[]
    /** 是否已完成（complete 事件） */
    isCompleted: boolean
    /** complete 事件携带的最终结论 */
    finalResult?: string
}

/** SSE subagent_event 事件载荷（对应后端 SubagentEventDto） */
export interface SubagentEventData {
    source: string
    agentId: string
    /** 专家中文显示名 */
    displayName?: string
    eventType: 'start' | 'text_chunk' | 'thinking_chunk' | 'tool_call_start' | 'tool_call_end' | 'complete'
    content?: string
    toolName?: string
    toolId?: string
    arguments?: Record<string, unknown>
    result?: string
}

/** 计划状态 */
export interface PlanState {
    title: string
    subtasks: Subtask[]
    isFinished: boolean
}

/** SSE task_progress 事件载荷（对应后端 TaskProgressDto） */
export interface TaskProgressEventData {
    /** 事件子类型 */
    eventType: 'plan_created' | 'task_updated' | 'task_completed' | 'plan_revised' | 'plan_finished'
    /** 计划标题 */
    planTitle?: string
    /** 子任务列表（create/revise 时发送完整列表） */
    subtasks?: {id: string; title: string; state: Subtask['state']}[]
    /** 被更新的子任务 ID */
    subtaskId?: string
    /** 被更新的子任务标题 */
    subtaskTitle?: string
    /** 新状态 */
    newState?: Subtask['state']
    /** 已完成子任务数 */
    completedCount?: number
    /** 子任务总数 */
    totalCount?: number
}

/** 聊天消息联合类型 */
export type ChatMessage = UserMessage | AssistantMessage | ToolCallMessage | ThinkingMessage | PlanMessage | CommandApprovalMessage | SubagentMessage

/** 后端返回的聊天消息 */
export interface BackendChatMessage {
    id: number
    sessionId: string
    role: 'user' | 'assistant' | 'tool'
    content: string
    toolName?: string
    toolId?: string
    arguments?: Record<string, unknown>
    result?: string
    fileIds?: number[]
    createdAt: number
    memoryRefs?: MemoryRecallInfo
    memoryMode?: 'NORMAL' | 'NO_MEMORY'
}

/** SSE 事件数据 */
export interface SSEEventData {
    type: string
    content?: string
    toolName?: string
    toolId?: string
    arguments?: Record<string, unknown>
    result?: string
    reason?: string
    error?: string
}

/** 消息搜索结果 */
export interface SearchResult {
    id: number
    sessionId: string
    sessionTitle: string
    role: 'user' | 'assistant'
    content: string
    createdAt: number
}
