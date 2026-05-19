import { ref, type Ref } from 'vue'
import type {
  EngineStatus,
  ExecuteRequest,
  ExecuteResult,
  WorkerMessage,
  WorkerResponse
} from '@/types/sandbox'

interface QueueItem {
  request: ExecuteRequest
  resolve: (result: ExecuteResult) => void
  reject: (error: Error) => void
}

/** 初始化最大等待时间（毫秒） */
const INIT_TIMEOUT = 60_000

export function usePythonEngine() {
  const status: Ref<EngineStatus> = ref('idle')
  const isRunning = ref(false)
  const currentOutput = ref('')

  let worker: Worker | null = null
  let currentId = 0
  const pendingRequests = new Map<string, { resolve: (r: ExecuteResult) => void; reject: (e: Error) => void }>()

  // 串行执行队列
  const queue: QueueItem[] = []
  let processing = false

  /** 获取 Pyodide 资源的绝对 URL */
  function getPyodideUrl(): string {
    if (import.meta.env.DEV) {
      return `${window.location.origin}/pyodide`
    }
    return new URL('./pyodide', window.location.href).href
  }

  /** 初始化引擎 */
  async function init(): Promise<void> {
    if (status.value === 'loading' || status.value === 'ready') return

    status.value = 'loading'
    worker = new Worker(new URL('@/workers/pyodide-worker.ts', import.meta.url), { type: 'module' })

    worker.onmessage = (event: MessageEvent<WorkerResponse>) => {
      const msg = event.data
      switch (msg.type) {
        case 'status':
          status.value = msg.status
          isRunning.value = msg.status === 'running'
          break
        case 'stdout':
          currentOutput.value += msg.data
          break
        case 'result': {
          const pending = pendingRequests.get(msg.id)
          if (pending) {
            pending.resolve(msg.result)
            pendingRequests.delete(msg.id)
          }
          break
        }
        case 'error': {
          const pending2 = pendingRequests.get(msg.id)
          if (pending2) {
            pending2.resolve({
              success: false,
              stdout: '',
              stderr: msg.error,
              duration: 0
            })
            pendingRequests.delete(msg.id)
          }
          break
        }
      }
    }

    worker.onerror = (error) => {
      console.error('Worker error:', error)
      status.value = 'error'
      // 拒绝所有等待中的请求，避免 Promise 泄漏
      for (const [, pending] of pendingRequests) {
        pending.resolve({
          success: false,
          stdout: '',
          stderr: `Worker 异常: ${error.message || '未知错误'}`,
          duration: 0
        })
      }
      pendingRequests.clear()
    }

    // 发送初始化消息
    const initMsg: WorkerMessage = { type: 'init', pyodideUrl: getPyodideUrl() }
    worker.postMessage(initMsg)

    // 等待初始化完成（带超时上限）
    return new Promise<void>((resolve, reject) => {
      const timeout = setTimeout(() => {
        if (status.value !== 'ready') {
          status.value = 'error'
          reject(new Error(`Pyodide 初始化超时（${INIT_TIMEOUT / 1000}秒）`))
        }
      }, INIT_TIMEOUT)

      const checkReady = () => {
        if (status.value === 'ready') {
          clearTimeout(timeout)
          resolve()
        } else if (status.value === 'error') {
          clearTimeout(timeout)
          reject(new Error('Pyodide 初始化失败'))
        } else {
          setTimeout(checkReady, 100)
        }
      }
      setTimeout(checkReady, 100)
    })
  }

  /** 执行代码（加入队列） */
  function execute(request: ExecuteRequest): Promise<ExecuteResult> {
    return new Promise((resolve, reject) => {
      queue.push({ request, resolve, reject })
      processQueue()
    })
  }

  /** 处理执行队列 */
  async function processQueue() {
    if (processing || queue.length === 0) return
    processing = true

    while (queue.length > 0) {
      const item = queue.shift()!

      // 检查引擎状态，非 ready 时直接返回错误
      if (!worker || status.value !== 'ready') {
        item.resolve({
          success: false,
          stdout: '',
          stderr: '沙箱未就绪，请等待初始化完成或重启内核',
          duration: 0
        })
        continue
      }

      try {
        const result = await doExecute(item.request)
        item.resolve(result)
      } catch (error) {
        item.reject(error as Error)
      }
    }

    processing = false
  }

  /** 实际执行代码 */
  function doExecute(request: ExecuteRequest): Promise<ExecuteResult> {
    if (!worker || status.value !== 'ready') {
      return Promise.resolve({
        success: false,
        stdout: '',
        stderr: '沙箱未就绪，请等待初始化完成',
        duration: 0
      })
    }

    const id = String(++currentId)
    currentOutput.value = ''

    return new Promise((resolve) => {
      let settled = false
      const timeout = request.timeout || 30000

      const settle = (result: ExecuteResult) => {
        if (settled) return
        settled = true
        clearTimeout(timer)
        pendingRequests.delete(id)
        resolve(result)
      }

      // 超时处理
      const timer = setTimeout(() => {
        settle({
          success: false,
          stdout: currentOutput.value,
          stderr: `执行超时（${timeout / 1000}秒）`,
          duration: timeout
        })
        // 超时后 terminate 并重建 Worker
        cancel()
      }, timeout)

      pendingRequests.set(id, {
        resolve: settle,
        reject: () => {
          settle({
            success: false,
            stdout: '',
            stderr: '执行被中断',
            duration: 0
          })
        }
      })

      // 构建传输列表（Transferable）
      const transferables: ArrayBuffer[] = []
      if (request.files) {
        for (const buffer of Object.values(request.files)) {
          transferables.push(buffer)
        }
      }

      const msg: WorkerMessage = {
        type: 'execute',
        id,
        code: request.code,
        files: request.files,
        globals: request.globals,
        timeout: request.timeout
      }

      if (transferables.length > 0) {
        worker!.postMessage(msg, transferables)
      } else {
        worker!.postMessage(msg)
      }
    })
  }

  /** 写入文件到沙箱 MEMFS */
  function writeFile(path: string, data: ArrayBuffer) {
    if (!worker) return
    const msg: WorkerMessage = { type: 'writeFile', path, data }
    worker.postMessage(msg, [data])
  }

  /** 加载工具代码到沙箱 */
  function loadTools(code: string) {
    if (!worker) return
    const msg: WorkerMessage = { type: 'loadTools', code }
    worker.postMessage(msg)
  }

  /** 清理 /data/ 目录下的文件 */
  function clearData() {
    if (!worker) return
    const msg: WorkerMessage = { type: 'clearData' }
    worker.postMessage(msg)
  }

  /** 取消当前执行（terminate Worker） */
  function cancel() {
    if (worker) {
      worker.terminate()
      worker = null
    }
    status.value = 'idle'
    isRunning.value = false

    // 拒绝所有等待中的请求
    for (const [, pending] of pendingRequests) {
      pending.resolve({
        success: false,
        stdout: '',
        stderr: '执行已取消',
        duration: 0
      })
    }
    pendingRequests.clear()

    // 清空队列，reject 所有排队中的请求
    while (queue.length > 0) {
      const item = queue.shift()!
      item.resolve({
        success: false,
        stdout: '',
        stderr: '执行已取消',
        duration: 0
      })
    }
    processing = false
  }

  /** 销毁引擎 */
  function destroy() {
    cancel()
  }

  return {
    status,
    isRunning,
    currentOutput,
    init,
    execute,
    writeFile,
    loadTools,
    clearData,
    cancel,
    destroy
  }
}
