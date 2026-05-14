import { defineStore } from 'pinia'
import { ref, reactive, computed, watch } from 'vue'
import type {
  EngineStatus,
  ExecuteRequest,
  ExecuteResult,
  ExecutionRecord,
  SandboxInstance
} from '@/types/sandbox'
import { usePythonEngine } from '@/composables/usePythonEngine'

// ── 沙箱配置 ──────────────────────────────────────
export interface SandboxSettings {
  /** 是否启用沙箱（关闭后代码块不显示运行按钮） */
  enabled: boolean
  /** 单次执行超时（秒） */
  execTimeout: number
  /** 空闲回收时间（分钟） */
  idleTimeout: number
  /** AI 返回代码块时是否自动执行 */
  autoRun: boolean
}

const SETTINGS_KEY = 'sandbox_settings'

const DEFAULT_SETTINGS: SandboxSettings = {
  enabled: true,
  execTimeout: 30,
  idleTimeout: 30,
  autoRun: false
}

function loadSettings(): SandboxSettings {
  try {
    const raw = localStorage.getItem(SETTINGS_KEY)
    if (raw) return { ...DEFAULT_SETTINGS, ...JSON.parse(raw) }
  } catch { /* ignore */ }
  return { ...DEFAULT_SETTINGS }
}

function saveSettings(settings: SandboxSettings): void {
  localStorage.setItem(SETTINGS_KEY, JSON.stringify(settings))
}

export const useSandboxStore = defineStore('sandbox', () => {
  // === Settings ===
  const settings = reactive<SandboxSettings>(loadSettings())

  // 配置变更时自动持久化
  watch(() => ({ ...settings }), (val) => {
    saveSettings(val)
  }, { deep: true })

  const enabled = computed(() => settings.enabled)

  // === State ===
  const engineStatus = ref<EngineStatus>('idle')
  const activeSessionId = ref<string | null>(null)
  const currentOutput = ref('')
  const instances = ref<Map<string, SandboxInstance>>(new Map())

  // 全局引擎实例（当前活跃的）
  let engine: ReturnType<typeof usePythonEngine> | null = null

  // === Computed ===
  const isReady = computed(() => engineStatus.value === 'ready')
  const isRunning = computed(() => engineStatus.value === 'running')

  // === Actions ===

  /** 初始化沙箱（应用启动时调用） */
  async function initEngine() {
    if (engine) return

    engine = usePythonEngine()
    engineStatus.value = 'loading'

    try {
      await engine.init()
      engineStatus.value = 'ready'
    } catch (error) {
      engineStatus.value = 'error'
      console.error('沙箱初始化失败:', error)
    }
  }

  /** 为指定会话执行代码 */
  async function execute(sessionId: string, code: string, files?: Record<string, ArrayBuffer>): Promise<ExecuteResult> {
    // 确保引擎已初始化
    if (!engine || engineStatus.value === 'idle') {
      await initEngine()
    }

    if (!engine || engineStatus.value !== 'ready') {
      return {
        success: false,
        stdout: '',
        stderr: '沙箱未就绪',
        duration: 0
      }
    }

    // 更新活跃会话
    activeSessionId.value = sessionId
    engineStatus.value = 'running'
    currentOutput.value = ''

    // 重置空闲计时器
    resetIdleTimer()

    const request: ExecuteRequest = { code, files }
    const result = await engine.execute(request)

    // 记录执行历史
    addHistory(sessionId, code, result)

    engineStatus.value = 'ready'
    return result
  }

  /** 同步工作目录文件到沙箱 */
  async function syncWorkdir(workdir: string) {
    if (!engine || !window.electronAPI) return

    const dataExts = ['.xlsx', '.xls', '.csv', '.json', '.txt', '.parquet']

    try {
      const fileNames = await window.electronAPI.fs.readDirectory(workdir)

      for (const fileName of fileNames) {
        const ext = fileName.substring(fileName.lastIndexOf('.')).toLowerCase()
        if (!dataExts.includes(ext)) continue

        const data = await window.electronAPI.fs.readFile(`${workdir}/${fileName}`)
        // Uint8Array → ArrayBuffer
        const buffer = data.buffer.slice(data.byteOffset, data.byteOffset + data.byteLength) as ArrayBuffer
        engine.writeFile(`/data/${fileName}`, buffer)
      }
    } catch (error) {
      console.error('工作目录同步失败:', error)
    }
  }

  /** 重启内核 */
  async function restart() {
    if (engine) {
      engine.destroy()
      engine = null
    }
    engineStatus.value = 'idle'
    currentOutput.value = ''
    await initEngine()
  }

  /** 销毁引擎 */
  function destroy() {
    if (engine) {
      engine.destroy()
      engine = null
    }
    clearIdleTimer()
    engineStatus.value = 'idle'
    instances.value.clear()
  }

  // === 空闲超时管理 ===
  let idleTimer: ReturnType<typeof setTimeout> | null = null

  function resetIdleTimer() {
    clearIdleTimer()
    const timeout = settings.idleTimeout * 60 * 1000
    idleTimer = setTimeout(() => {
      console.log('沙箱空闲超时，自动回收')
      if (engine) {
        engine.destroy()
        engine = null
      }
      engineStatus.value = 'idle'
    }, timeout)
  }

  function clearIdleTimer() {
    if (idleTimer) {
      clearTimeout(idleTimer)
      idleTimer = null
    }
  }

  // === 执行历史 ===
  const history = ref<ExecutionRecord[]>([])

  function addHistory(sessionId: string, code: string, result: ExecuteResult) {
    history.value.push({
      id: `${sessionId}-${Date.now()}`,
      code,
      result,
      timestamp: Date.now()
    })
    // 只保留最近 50 条
    if (history.value.length > 50) {
      history.value = history.value.slice(-50)
    }
  }

  return {
    // Settings
    settings,
    enabled,
    // State
    engineStatus,
    activeSessionId,
    currentOutput,
    history,
    // Computed
    isReady,
    isRunning,
    // Actions
    initEngine,
    execute,
    syncWorkdir,
    restart,
    destroy
  }
})
