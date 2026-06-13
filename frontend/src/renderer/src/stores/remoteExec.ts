import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useSandboxStore } from '@/stores/sandbox'
import type {
  RemoteExecStatus,
  RemoteExecSettings,
  PendingCommand,
  WsMessage,
  CommandRequestPayload,
  CommandResult,
  ExecPolicy
} from '@/types/remote-exec'
import { WS_MESSAGE_TYPES } from '@/types/remote-exec'
import type { ExecuteResult } from '@/types/sandbox'

// ── 配置持久化 ──────────────────────────────────────
const SETTINGS_KEY = 'remote_exec_settings'
const SETTINGS_VERSION = 2

const DEFAULT_SETTINGS: RemoteExecSettings = {
  settingsVersion: SETTINGS_VERSION,
  enabled: true,
  autoExecuteLowRisk: false,
  defaultWorkDir: ''
}

function loadSettings(): RemoteExecSettings {
  try {
    const raw = localStorage.getItem(SETTINGS_KEY)
    if (raw) {
      const parsed = JSON.parse(raw) as Partial<RemoteExecSettings>
      const settings = { ...DEFAULT_SETTINGS, ...parsed }
      if (parsed.settingsVersion !== SETTINGS_VERSION) {
        settings.autoExecuteLowRisk = false
        settings.settingsVersion = SETTINGS_VERSION
        saveSettings(settings)
      }
      return settings
    }
  } catch { /* ignore */ }
  return { ...DEFAULT_SETTINGS }
}

function saveSettings(settings: RemoteExecSettings): void {
  localStorage.setItem(SETTINGS_KEY, JSON.stringify(settings))
}

/**
 * 构建执行隔离策略：
 * - allowedRoots 由客户端掌握（用户授权的工作目录），后端不下发目录边界。
 * - resourceLimits.timeoutMs 取后端下发值（payload.timeoutMs）。
 * - 后端若下发 policy，仅合并资源限制；隔离级别始终由客户端固定为 boundary。
 */
function buildPolicy(workingDir: string, payload: CommandRequestPayload): ExecPolicy {
  const backendPolicy = payload.policy
  const allowedRoots = workingDir && workingDir.trim() ? [workingDir] : []
  return {
    allowedRoots,
    resourceLimits: {
      timeoutMs: payload.timeoutMs,
      ...backendPolicy?.resourceLimits
    },
    isolationLevel: 'boundary'
  }
}

// ── WebSocket URL 构建 ──────────────────────────────
function buildWsUrl(): string {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
  const token = localStorage.getItem('auth_token') || ''
  const wsUrl = baseUrl.replace(/^http/, 'ws')
  return `${wsUrl}/ws/remote-exec?token=${token}`
}

export const useRemoteExecStore = defineStore('remoteExec', () => {
  // === State ===
  const settings = ref<RemoteExecSettings>(loadSettings())
  const status = ref<RemoteExecStatus>('disconnected')
  const pendingCommands = ref<PendingCommand[]>([])
  const executionHistory = ref<Array<{ command: string; result: CommandResult; timestamp: number }>>([])
  /**
   * Agent 触发的沙箱执行结果（含图表），供聊天界面渲染。
   * 每次 Agent 调用 sandbox_exec 后写入最新一条，ChatView 监听后绑定到当前流式助手消息。
   */
  const lastSandboxResult = ref<{ sessionId: string; result: ExecuteResult; seq: number } | null>(null)
  let sandboxResultSeq = 0
  /** 已授权"本次全部允许"的会话 ID 集合（内存态，刷新重置） */
  const autoApprovedSessions = ref<Set<string>>(new Set())
  // WebSocket 实例（非响应式）
  let ws: WebSocket | null = null
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null
  let reconnectAttempts = 0
  const MAX_RECONNECT_ATTEMPTS = 20
  const BASE_RECONNECT_DELAY = 1000
  const MAX_RECONNECT_DELAY = 30000

  // === Computed ===
  const isConnected = computed(() => status.value === 'connected')
  const hasPendingCommands = computed(() => pendingCommands.value.length > 0)

  // === Settings 持久化 ===
  watch(settings, (val) => saveSettings(val), { deep: true })

  // === WebSocket 连接管理 ===

  function connect() {
    const authStore = useAuthStore()
    if (!authStore.isLoggedIn || !settings.value.enabled) return
    if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) return

    status.value = 'connecting'
    const url = buildWsUrl()

    ws = new WebSocket(url)

    ws.onopen = async () => {
      status.value = 'connected'
      reconnectAttempts = 0
      // 获取详细平台信息并发送 client_ready
      let platformInfo: string = navigator.platform
      try {
        const info = await window.electronAPI.app.getPlatformInfo()
        platformInfo = `${info.platform}|${info.arch}|${info.release}`
      } catch { /* fallback to navigator.platform */ }
      sendMessage({
        type: WS_MESSAGE_TYPES.CLIENT_READY,
        requestId: null,
        sessionId: null,
        timestamp: Date.now(),
        payload: {
          platform: platformInfo,
          version: '1.0'
        }
      })
    }

    ws.onmessage = (event) => {
      try {
        const msg: WsMessage = JSON.parse(event.data)
        handleMessage(msg)
      } catch (e) {
        console.warn('[RemoteExec] 解析消息失败:', e)
      }
    }

    ws.onclose = () => {
      status.value = 'disconnected'
      ws = null
      scheduleReconnect()
    }

    ws.onerror = () => {
      status.value = 'error'
    }
  }

  function disconnect() {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    reconnectAttempts = MAX_RECONNECT_ATTEMPTS // 阻止自动重连
    if (ws) {
      ws.close()
      ws = null
    }
    status.value = 'disconnected'
  }

  function scheduleReconnect() {
    if (!settings.value.enabled) return
    if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) return

    const delay = Math.min(BASE_RECONNECT_DELAY * Math.pow(2, reconnectAttempts), MAX_RECONNECT_DELAY)
    reconnectAttempts++

    reconnectTimer = setTimeout(() => {
      reconnectTimer = null
      connect()
    }, delay)
  }

  // === 消息处理 ===

  function handleMessage(msg: WsMessage) {
    switch (msg.type) {
      case WS_MESSAGE_TYPES.PING:
        sendMessage({ type: WS_MESSAGE_TYPES.PONG, requestId: null, sessionId: null, timestamp: Date.now(), payload: null })
        break

      case WS_MESSAGE_TYPES.COMMAND_REQUEST:
        handleCommandRequest(msg)
        break

      case WS_MESSAGE_TYPES.SANDBOX_EXEC_REQUEST:
        handleSandboxExecRequest(msg)
        break

      case WS_MESSAGE_TYPES.COMMAND_CANCEL:
        handleCommandCancel(msg)
        break

      case WS_MESSAGE_TYPES.CONNECTED:
        console.log('[RemoteExec] 服务端确认连接')
        break

      default:
        console.warn('[RemoteExec] 未知消息类型:', msg.type)
    }
  }

  async function handleCommandRequest(msg: WsMessage) {
    const payload = msg.payload as unknown as CommandRequestPayload
    if (!payload || !msg.requestId) return

    const workingDir = payload.workingDir || settings.value.defaultWorkDir

    const pending: PendingCommand = {
      requestId: msg.requestId,
      sessionId: msg.sessionId || '',
      command: payload.command,
      workingDir,
      riskLevel: payload.riskLevel,
      timeoutMs: payload.timeoutMs,
      receivedAt: Date.now(),
      policy: buildPolicy(workingDir, payload)
    }

    // 低风险 + 自动执行 → 直接执行
    if (payload.riskLevel === 'LOW' && settings.value.autoExecuteLowRisk) {
      await executeCommand(pending)
    } else if (autoApprovedSessions.value.has(pending.sessionId)) {
      // 该会话已授权"全部允许" → 直接执行
      await executeCommand(pending)
    } else {
      // 高风险或未开启自动执行 → 加入待审批队列，ToolCallCard 会检测并显示按钮
      pendingCommands.value.push(pending)
      // 超时自动拒绝
      setTimeout(() => {
        const still = pendingCommands.value.find(c => c.requestId === pending.requestId)
        if (still) {
          rejectCommand(pending.requestId, 'timeout')
        }
      }, pending.timeoutMs)
    }
  }

  function handleCommandCancel(msg: WsMessage) {
    if (!msg.requestId) return
    pendingCommands.value = pendingCommands.value.filter(c => c.requestId !== msg.requestId)
  }

  /** 处理沙箱代码执行请求（自动执行，无需审批） */
  async function handleSandboxExecRequest(msg: WsMessage) {
    const payload = msg.payload as { code: string } | null
    if (!payload || !payload.code || !msg.requestId) return

    try {
      const sandboxStore = useSandboxStore()

      const sessionId = msg.sessionId || 'default'
      const result = await sandboxStore.execute(sessionId, payload.code)

      // 把执行结果（含图表）暴露给聊天界面渲染（绑定到当前流式助手消息）
      lastSandboxResult.value = { sessionId, result, seq: ++sandboxResultSeq }

      // 回传执行结果
      sendMessage({
        type: WS_MESSAGE_TYPES.SANDBOX_EXEC_RESULT,
        requestId: msg.requestId,
        sessionId: msg.sessionId,
        timestamp: Date.now(),
        payload: {
          success: result.success,
          stdout: result.stdout || '',
          stderr: result.stderr || '',
          result: result.result != null ? String(result.result) : '',
          figureCount: result.figures?.length || 0,
          durationMs: result.duration
        }
      })
    } catch (e: any) {
      sendMessage({
        type: WS_MESSAGE_TYPES.SANDBOX_EXEC_RESULT,
        requestId: msg.requestId,
        sessionId: msg.sessionId,
        timestamp: Date.now(),
        payload: {
          success: false,
          stdout: '',
          stderr: e.message || '沙箱执行失败',
          result: '',
          figureCount: 0,
          durationMs: 0
        }
      })
    }
  }

  // === 命令执行 ===

  async function executeCommand(cmd: PendingCommand) {
    // 从待审批队列移除
    pendingCommands.value = pendingCommands.value.filter(c => c.requestId !== cmd.requestId)

    try {
      // policy 来自响应式 ref（pendingCommands），是嵌套的 Vue Proxy；
      // 直接经 IPC 传递会触发 structuredClone 失败（An object could not be cloned）。
      // policy 仅含字符串/数字/数组等可序列化值，用 JSON 深拷贝彻底剥离所有层级的 Proxy。
      const rawPolicy = cmd.policy ? JSON.parse(JSON.stringify(cmd.policy)) : undefined
      const result = await window.electronAPI.shell.execute(cmd.command, cmd.workingDir || undefined, rawPolicy)

      // 记录历史
      executionHistory.value.unshift({ command: cmd.command, result, timestamp: Date.now() })
      if (executionHistory.value.length > 50) executionHistory.value.pop()

      // 回传结果
      sendMessage({
        type: WS_MESSAGE_TYPES.COMMAND_RESULT,
        requestId: cmd.requestId,
        sessionId: cmd.sessionId,
        timestamp: Date.now(),
        payload: {
          exitCode: result.exitCode,
          stdout: result.stdout,
          stderr: result.stderr,
          durationMs: result.durationMs
        }
      })
    } catch (e) {
      sendMessage({
        type: WS_MESSAGE_TYPES.COMMAND_RESULT,
        requestId: cmd.requestId,
        sessionId: cmd.sessionId,
        timestamp: Date.now(),
        payload: {
          exitCode: -1,
          stdout: '',
          stderr: e instanceof Error ? e.message : '执行失败',
          durationMs: 0
        }
      })
    }
  }

  function rejectCommand(requestId: string, reason: string = 'user_denied') {
    pendingCommands.value = pendingCommands.value.filter(c => c.requestId !== requestId)
    sendMessage({
      type: WS_MESSAGE_TYPES.COMMAND_REJECTED,
      requestId,
      sessionId: null,
      timestamp: Date.now(),
      payload: { reason }
    })
  }

  async function approveCommand(requestId: string) {
    const cmd = pendingCommands.value.find(c => c.requestId === requestId)
    if (cmd) {
      pendingCommands.value = pendingCommands.value.filter(c => c.requestId !== requestId)
      await executeCommand(cmd)
    }
  }

  /** 本次会话全部允许: 标记 sessionId 并立即执行该会话所有 pending 命令 */
  async function approveSession(sessionId: string) {
    autoApprovedSessions.value = new Set([...autoApprovedSessions.value, sessionId])
    // 立即执行该会话所有待审批命令
    const sessionPending = pendingCommands.value.filter(c => c.sessionId === sessionId)
    pendingCommands.value = pendingCommands.value.filter(c => c.sessionId !== sessionId)
    for (const cmd of sessionPending) {
      await executeCommand(cmd)
    }
  }

  // === 工具方法 ===

  function sendMessage(msg: WsMessage) {
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify(msg))
    }
  }

  function updateSettings(partial: Partial<RemoteExecSettings>) {
    Object.assign(settings.value, partial)
    // 启用/禁用时自动连接/断开
    if ('enabled' in partial) {
      if (partial.enabled) {
        connect()
      } else {
        disconnect()
      }
    }
  }

  return {
    // State
    settings,
    status,
    pendingCommands,
    executionHistory,
    lastSandboxResult,
    // Computed
    isConnected,
    hasPendingCommands,
    // Actions
    connect,
    disconnect,
    approveCommand,
    approveSession,
    rejectCommand,
    updateSettings
  }
})
