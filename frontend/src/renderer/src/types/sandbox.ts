/** 代码执行请求 */
export interface ExecuteRequest {
  /** Python 代码 */
  code: string
  /** 注入到沙箱的文件（文件名 → ArrayBuffer） */
  files?: Record<string, ArrayBuffer>
  /** 注入到 Python 全局变量的数据 */
  globals?: Record<string, unknown>
  /** 超时时间（毫秒），默认 30000 */
  timeout?: number
}

/** 代码执行结果 */
export interface ExecuteResult {
  success: boolean
  /** Python 表达式的返回值 */
  result?: unknown
  /** stdout 输出 */
  stdout: string
  /** stderr 输出 */
  stderr: string
  /** 生成的文件（文件名 → base64） */
  outputFiles?: Record<string, string>
  /** 生成的图表（base64 PNG） */
  figures?: string[]
  /** 执行耗时（毫秒） */
  duration: number
}

/** 执行引擎状态 */
export type EngineStatus = 'idle' | 'loading' | 'ready' | 'running' | 'error'

/** 执行引擎接口（未来可替换为远程沙箱实现） */
export interface ICodeEngine {
  status: EngineStatus
  init(): Promise<void>
  execute(request: ExecuteRequest): Promise<ExecuteResult>
  cancel(): void
  destroy(): void
}

/** Worker 发送给主线程的消息 */
export type WorkerResponse =
  | { type: 'status'; status: EngineStatus }
  | { type: 'stdout'; data: string }
  | { type: 'result'; id: string; result: ExecuteResult }
  | { type: 'error'; id: string; error: string }

/** 主线程发送给 Worker 的消息 */
export type WorkerMessage =
  | { type: 'init'; pyodideUrl: string }
  | { type: 'execute'; id: string; code: string; files?: Record<string, ArrayBuffer>; globals?: Record<string, unknown>; timeout?: number }
  | { type: 'writeFile'; path: string; data: ArrayBuffer }

/** 执行记录 */
export interface ExecutionRecord {
  id: string
  code: string
  result: ExecuteResult
  timestamp: number
}
