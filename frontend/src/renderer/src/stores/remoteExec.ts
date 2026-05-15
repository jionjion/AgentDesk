import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import type {
  RemoteExecStatus,
  RemoteExecSettings,
  PendingCommand,
  WsMessage,
  CommandRequestPayload,
  CommandResult
} from '@/types/remote-exec'
import type { CommandApprovalMessage } from '@/types/chat'
import { WS_MESSAGE_TYPES } from '@/types/remote-exec'

// ── 配置持久化 ──────────────────────────────────────
const SETTINGS_KEY = 'remote_exec_settings'

const DEFAULT_SETTINGS: RemoteExecSettings = {
  enabled: true,
  autoExecuteLowRisk: true,
  defaultWorkDir: ''
}

function loadSettings(): RemoteExecSettings {
  try {
    const raw = localStorage.getItem(SETTINGS_KEY)
    if (raw) return { ...DEFAULT_SETTINGS, ...JSON.parse(raw) }
  } catch { /* ignore */ }
  return { ...DEFAULT_SETTINGS }
}

function saveSettings(settings: RemoteExecSettings): void {
  localStorage.setItem(SETTINGS_KEY, JSON.stringify(settings))
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

    const pending: PendingCommand = {
      requestId: msg.requestId,
      sessionId: msg.sessionId || '',
      command: payload.command,
      workingDir: payload.workingDir || settings.value.defaultWorkDir,
      riskLevel: payload.riskLevel,
      timeoutMs: payload.timeoutMs,
      receivedAt: Date.now()
    }

    // 低风险 + 自动执行 → 直接执行
    if (payload.riskLevel === 'LOW' && settings.value.autoExecuteLowRisk) {
      await executeCommand(pending)
    } else {
      // 高风险或未开启自动执行 → 加入待审批队列并注入聊天气泡
      pendingCommands.value.push(pending)
      injectApprovalMessage(pending)
    }
  }

  function handleCommandCancel(msg: WsMessage) {
    if (!msg.requestId) return
    pendingCommands.value = pendingCommands.value.filter(c => c.requestId !== msg.requestId)
    updateApprovalMessageStatus(msg.requestId, 'cancelled')
  }

  // === 命令执行 ===

  async function executeCommand(cmd: PendingCommand) {
    // 从待审批队列移除
    pendingCommands.value = pendingCommands.value.filter(c => c.requestId !== cmd.requestId)

    try {
      const result = await window.electronAPI.shell.execute(cmd.command, cmd.workingDir || undefined)

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
    updateApprovalMessageStatus(requestId, 'rejected')
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
      updateApprovalMessageStatus(requestId, 'approved')
      await executeCommand(cmd)
    }
  }

  // === 审批消息注入 ===

  function injectApprovalMessage(pending: PendingCommand) {
    const chatStore = useChatStore()
    const approvalMsg: CommandApprovalMessage = {
      id: `approval-${pending.requestId}`,
      role: 'command_approval',
      requestId: pending.requestId,
      sessionId: pending.sessionId,
      command: pending.command,
      workingDir: pending.workingDir,
      riskLevel: pending.riskLevel,
      timeoutMs: pending.timeoutMs,
      receivedAt: pending.receivedAt,
      status: 'pending',
      timestamp: Date.now()
    }

    // 注入到对应会话的消息列表
    chatStore.injectMessage(pending.sessionId, approvalMsg)

    // 超时自动拒绝
    setTimeout(() => {
      const msg = findApprovalMessage(pending.requestId)
      if (msg && msg.status === 'pending') {
        updateApprovalMessageStatus(pending.requestId, 'timeout')
        rejectCommand(pending.requestId, 'timeout')
      }
    }, pending.timeoutMs)
  }

  function updateApprovalMessageStatus(requestId: string, newStatus: 'approved' | 'rejected' | 'timeout' | 'cancelled') {
    const msg = findApprovalMessage(requestId)
    if (msg) {
      msg.status = newStatus
    }
  }

  function findApprovalMessage(requestId: string): CommandApprovalMessage | null {
    const chatStore = useChatStore()
    const allMessages = chatStore.messagesBySession
    for (const msgs of Object.values(allMessages)) {
      const found = msgs.find(m => m.role === 'command_approval' && (m as CommandApprovalMessage).requestId === requestId)
      if (found) return found as CommandApprovalMessage
    }
    return null
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
    // Computed
    isConnected,
    hasPendingCommands,
    // Actions
    connect,
    disconnect,
    approveCommand,
    rejectCommand,
    updateSettings
  }
})
