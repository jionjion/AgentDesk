import { defineStore } from 'pinia'
import { ref, reactive, computed, watch, watchEffect } from 'vue'
import type {
  EngineStatus,
  ExecuteRequest,
  ExecuteResult,
  ExecutionRecord
} from '@/types/sandbox'
import { usePythonEngine } from '@/composables/usePythonEngine'
import { useSandboxToolsStore } from '@/stores/sandbox-tools'

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

  /** 当前工作目录（用户选择的，沙箱只能访问此目录下的文件） */
  const workdir = ref<string | null>(null)

  /** 已同步到 MEMFS 的文件列表 */
  const syncedFiles = ref<string[]>([])

  // 全局引擎实例（当前活跃的）
  let engine: ReturnType<typeof usePythonEngine> | null = null

  // === Computed ===
  const isReady = computed(() => engineStatus.value === 'ready')
  const isRunning = computed(() => engineStatus.value === 'running')
  const hasWorkdir = computed(() => !!workdir.value)

  // === Actions ===

  /** 初始化沙箱（应用启动时调用） */
  async function initEngine() {
    if (engine) return

    engine = usePythonEngine()

    // 自动同步引擎状态到 store
    watchEffect(() => {
      if (engine) {
        engineStatus.value = engine.status.value
      }
    })

    try {
      await engine.init()
      // 注入沙箱工具
      const toolsStore = useSandboxToolsStore()
      const toolsCode = toolsStore.getEnabledToolsCode()
      if (toolsCode) {
        engine.loadTools(toolsCode)
      }
    } catch (error) {
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
    currentOutput.value = ''

    // 重置空闲计时器
    resetIdleTimer()

    const request: ExecuteRequest = {
      code,
      files,
      timeout: settings.execTimeout * 1000
    }
    const result = await engine.execute(request)

    // 记录执行历史
    addHistory(sessionId, code, result)

    return result
  }

  /** 设置工作目录 */
  async function setWorkdir(dir: string) {
    workdir.value = dir
    syncedFiles.value = []
    // 确保引擎已初始化再同步文件
    if (!engine || engineStatus.value === 'idle') {
      await initEngine()
    }
    // 清理旧的 /data/ 文件，避免切换工作目录后旧文件残留
    if (engine) {
      engine.clearData()
    }
    await syncWorkdir(dir)
  }

  /** 清除工作目录（同时清理沙箱中的文件） */
  function clearWorkdir() {
    workdir.value = null
    syncedFiles.value = []
    if (engine) {
      engine.clearData()
    }
  }

  /** 检查文件路径是否在工作目录内 */
  function isPathAllowed(filePath: string): boolean {
    if (!workdir.value) return false
    // 规范化路径分隔符
    const normalizedWorkdir = workdir.value.replace(/\\/g, '/').toLowerCase()
    const normalizedPath = filePath.replace(/\\/g, '/').toLowerCase()
    return normalizedPath.startsWith(normalizedWorkdir + '/') || normalizedPath === normalizedWorkdir
  }

  /** 将本地文件同步到沙箱（带权限检查） */
  async function syncFile(filePath: string): Promise<{ success: boolean; error?: string }> {
    if (!workdir.value) {
      return { success: false, error: '未设置工作目录，请先选择一个工作目录' }
    }
    if (!isPathAllowed(filePath)) {
      return { success: false, error: `文件不在工作目录内，拒绝访问。工作目录: ${workdir.value}` }
    }
    if (!engine || !window.electronAPI) {
      return { success: false, error: '沙箱未就绪' }
    }

    try {
      const data = await window.electronAPI.fs.readFile(filePath)
      const buffer = data.buffer.slice(data.byteOffset, data.byteOffset + data.byteLength) as ArrayBuffer
      const fileName = filePath.replace(/\\/g, '/').split('/').pop()!
      engine.writeFile(`/data/${fileName}`, buffer)
      if (!syncedFiles.value.includes(fileName)) {
        syncedFiles.value.push(fileName)
      }
      return { success: true }
    } catch (error: any) {
      return { success: false, error: `文件读取失败: ${error.message || error}` }
    }
  }

  /** 同步工作目录文件到沙箱（递归子目录） */
  async function syncWorkdir(dir: string) {
    if (!engine || !window.electronAPI) return

    const dataExts = ['.xlsx', '.xls', '.csv', '.json', '.txt', '.parquet', '.pdf', '.docx']

    try {
      // 递归获取所有匹配扩展名的文件（相对路径）
      const relativePaths = await window.electronAPI.fs.readDirectoryRecursive(dir, dataExts)
      syncedFiles.value = []

      for (const relPath of relativePaths) {
        const fullPath = `${dir}/${relPath}`
        const data = await window.electronAPI.fs.readFile(fullPath)
        const buffer = data.buffer.slice(data.byteOffset, data.byteOffset + data.byteLength) as ArrayBuffer
        // 保持目录结构写入沙箱: /data/子目录/文件名
        engine.writeFile(`/data/${relPath}`, buffer)
        syncedFiles.value.push(relPath)
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

  /** 获取沙箱上下文（发消息时附带给后端） */
  function getSandboxContext(): { tools: import('@/types/sandbox-tools').SandboxToolMeta[]; files: string[] } | null {
    if (!settings.enabled) return null
    const toolsStore = useSandboxToolsStore()
    return {
      tools: toolsStore.getToolMetadata(),
      files: syncedFiles.value
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
    workdir,
    syncedFiles,
    // Computed
    isReady,
    isRunning,
    hasWorkdir,
    // Actions
    initEngine,
    execute,
    setWorkdir,
    clearWorkdir,
    isPathAllowed,
    syncFile,
    syncWorkdir,
    restart,
    destroy,
    getSandboxContext
  }
})
