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
}

/** 远程执行设置 */
export interface RemoteExecSettings {
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
  PING: 'ping',
  PONG: 'pong',
  CONNECTED: 'connected',
  CLIENT_READY: 'client_ready'
} as const
