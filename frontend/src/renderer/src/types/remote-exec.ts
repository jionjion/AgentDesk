/**
 * 远程命令执行相关类型定义
 */

/** WebSocket 消息格式 */
export interface WsMessage {
  type: string
  requestId: string | null
  sessionId: string | null
  timestamp: number
  payload: Record<string, unknown> | null
}

/** 命令请求载荷 */
export interface CommandRequestPayload {
  command: string
  workingDir: string
  riskLevel: 'LOW' | 'HIGH'
  timeoutMs: number
  /** 后端下发的执行隔离策略（可选） */
  policy?: ExecPolicy
}

/** 执行隔离策略（与 preload ExecPolicy 对应） */
export interface ExecPolicy {
  /** 允许执行的根目录树（绝对路径） */
  allowedRoots?: string[]
  resourceLimits?: {
    timeoutMs?: number
    maxOutputChars?: number
    memMB?: number
    maxProcesses?: number
  }
  isolationLevel?: 'boundary'
}

/** 命令执行结果 */
export interface CommandResult {
  exitCode: number
  stdout: string
  stderr: string
  durationMs: number
}

/** 远程执行连接状态 */
export type RemoteExecStatus = 'disconnected' | 'connecting' | 'connected' | 'error'

/** 待审批的命令 */
export interface PendingCommand {
  requestId: string
  sessionId: string
  command: string
  workingDir: string
  riskLevel: 'LOW' | 'HIGH'
  timeoutMs: number
  receivedAt: number
  /** 执行隔离策略 */
  policy?: ExecPolicy
}

/** 远程执行设置 */
export interface RemoteExecSettings {
  /** 本地设置结构版本, 用于安全默认值迁移 */
  settingsVersion: number
  /** 是否启用远程执行 */
  enabled: boolean
  /** 自动执行低风险命令 */
  autoExecuteLowRisk: boolean
  /** 默认工作目录 */
  defaultWorkDir: string
}

/** WebSocket 消息类型常量 */
export const WS_MESSAGE_TYPES = {
  COMMAND_REQUEST: 'command_request',
  COMMAND_RESULT: 'command_result',
  COMMAND_REJECTED: 'command_rejected',
  COMMAND_CANCEL: 'command_cancel',
  SANDBOX_EXEC_REQUEST: 'sandbox_exec_request',
  SANDBOX_EXEC_RESULT: 'sandbox_exec_result',
  RUNTIME_SNAPSHOT_REQUEST: 'runtime_snapshot_request',
  RUNTIME_SNAPSHOT_RESULT: 'runtime_snapshot_result',
  PING: 'ping',
  PONG: 'pong',
  CONNECTED: 'connected',
  CLIENT_READY: 'client_ready'
} as const
