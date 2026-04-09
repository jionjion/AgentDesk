/**
 * 聊天相关类型定义
 */

/** 会话 */
export interface ChatSession {
  id: string
  title: string
  createdAt: number
  lastUsedAt: number
}

/** 附件信息 */
export interface Attachment {
  id: number
  name: string
  size: number
  contentType: string
}

/** 用户消息 */
export interface UserMessage {
  id: string
  role: 'user'
  content: string
  timestamp: number
  attachments?: Attachment[]
}

/** 助手消息 */
export interface AssistantMessage {
  id: string
  role: 'assistant'
  content: string
  timestamp: number
  isStreaming: boolean
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

/** 子任务 */
export interface Subtask {
  name: string
  state: 'todo' | 'in_progress' | 'done' | 'abandoned'
}

/** 计划状态 */
export interface PlanState {
  title: string
  subtasks: Subtask[]
  isFinished: boolean
}

/** 聊天消息联合类型 */
export type ChatMessage = UserMessage | AssistantMessage | ToolCallMessage | ThinkingMessage | PlanMessage

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
