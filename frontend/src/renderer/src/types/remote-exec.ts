/**
 * 本地执行相关类型定义（统一 local_exec 协议, 见开发计划 8.2/8.3）
 */

/** WebSocket 消息格式 */
export interface WsMessage {
  type: string
  requestId: string | null
  sessionId: string | null
  timestamp: number
  payload: Record<string, unknown> | null
}

/** 统一本地执行请求载荷 (shell/python) */
export interface LocalExecRequestPayload {
  kind: 'shell' | 'python'
  command?: string
  code?: string
  scriptPath?: string
  args?: string[]
  cwd?: string
  projectId?: string
  deviceId?: string
  riskLevel: 'LOW' | 'HIGH'
  timeoutMs: number
  /** 后端下发的资源限制 */
  resourceLimits?: ResourceLimits
}

/** 资源限制（后端下发，客户端 Job Object/超时/输出截断使用） */
export interface ResourceLimits {
  timeoutMs?: number
  maxOutputChars?: number
  memMB?: number
  maxProcesses?: number
}

/** 命令执行结果 */
export interface CommandResult {
  exitCode: number
  stdout: string
  stderr: string
  durationMs: number
}

/** 本地文件 RPC 请求载荷 */
export interface LocalFsRequestPayload {
  op: 'read' | 'write' | 'edit' | 'list' | 'glob' | 'grep' | 'stat'
  path: string
  projectId?: string
  deviceId?: string
  riskLevel?: 'LOW' | 'HIGH'
  offset?: number
  limit?: number
  content?: string
  oldText?: string
  newText?: string
  replaceAll?: boolean
  expectedReplacements?: number
  pattern?: string
  query?: string
  filePattern?: string
}

/** 远程执行连接状态 */
export type RemoteExecStatus = 'disconnected' | 'connecting' | 'connected' | 'error'

/** 待审批的执行请求 */
export interface PendingCommand {
  requestId: string
  sessionId: string
  /** 展示用摘要: shell 为命令原文, python 为代码/脚本摘要, fs 为操作描述 */
  command: string
  /** 请求类别 */
  kind: 'shell' | 'python' | 'fs'
  workingDir: string
  riskLevel: 'LOW' | 'HIGH'
  timeoutMs: number
  receivedAt: number
  /** 原始 exec 载荷 (kind=shell/python) */
  execPayload?: LocalExecRequestPayload
  /** 原始 fs 载荷 (kind=fs, 写入/编辑需审批) */
  fsPayload?: LocalFsRequestPayload
}

/** 远程执行设置 */
export interface RemoteExecSettings {
  /** 本地设置结构版本, 用于安全默认值迁移 */
  settingsVersion: number
  /** 是否启用远程执行 */
  enabled: boolean
  /** 自动执行低风险命令 */
  autoExecuteLowRisk: boolean
}

/** WebSocket 消息类型常量 */
export const WS_MESSAGE_TYPES = {
  LOCAL_EXEC_REQUEST: 'local_exec_request',
  LOCAL_EXEC_RESULT: 'local_exec_result',
  LOCAL_EXEC_REJECTED: 'local_exec_rejected',
  LOCAL_EXEC_CANCEL: 'local_exec_cancel',
  LOCAL_FS_REQUEST: 'local_fs_request',
  LOCAL_FS_RESULT: 'local_fs_result',
  RUNTIME_SNAPSHOT_REQUEST: 'runtime_snapshot_request',
  RUNTIME_SNAPSHOT_RESULT: 'runtime_snapshot_result',
  PING: 'ping',
  PONG: 'pong',
  CONNECTED: 'connected',
  CLIENT_READY: 'client_ready'
} as const
