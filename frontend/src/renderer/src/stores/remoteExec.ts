import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import { useAuthStore } from '@/stores/auth'
import type {
  RemoteExecStatus,
  RemoteExecSettings,
  PendingCommand,
  WsMessage,
  LocalExecRequestPayload,
  LocalFsRequestPayload
} from '@/types/remote-exec'
import { WS_MESSAGE_TYPES } from '@/types/remote-exec'

// ── 配置持久化 ──────────────────────────────────────
const SETTINGS_KEY = 'remote_exec_settings'
const SETTINGS_VERSION = 3

const DEFAULT_SETTINGS: RemoteExecSettings = {
  settingsVersion: SETTINGS_VERSION,
  enabled: true,
  autoExecuteLowRisk: false
}

function loadSettings(): RemoteExecSettings {
  try {
    const raw = localStorage.getItem(SETTINGS_KEY)
    if (raw) {
      const parsed = JSON.parse(raw) as Partial<RemoteExecSettings> & { defaultWorkDir?: string }
      const settings: RemoteExecSettings = {
        settingsVersion: SETTINGS_VERSION,
        enabled: parsed.enabled ?? DEFAULT_SETTINGS.enabled,
        autoExecuteLowRisk: parsed.autoExecuteLowRisk ?? DEFAULT_SETTINGS.autoExecuteLowRisk
      }
      if (parsed.settingsVersion !== SETTINGS_VERSION) {
        // 版本迁移: 重置自动执行开关为安全默认值; defaultWorkDir 已废弃（项目机制取代）
        settings.autoExecuteLowRisk = false
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

// ── WebSocket URL 构建 ──────────────────────────────
function buildWsUrl(): string {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
  const token = localStorage.getItem('auth_token') || ''
  const wsUrl = baseUrl.replace(/^http/, 'ws')
  return `${wsUrl}/ws/remote-exec?token=${token}`
}

/** 为 pending 队列生成展示摘要 */
function summarizeExec(payload: LocalExecRequestPayload): string {
  if (payload.kind === 'python') {
    if (payload.scriptPath) {
      return `python ${payload.scriptPath}${payload.args?.length ? ' ' + payload.args.join(' ') : ''}`
    }
    const code = payload.code || ''
    const firstLines = code.split('\n').slice(0, 8).join('\n')
    return `python -（内联代码）\n${firstLines}${code.split('\n').length > 8 ? '\n…' : ''}`
  }
  return payload.command || ''
}

function summarizeFs(payload: LocalFsRequestPayload): string {
  const opText = payload.op === 'write' ? '写入文件' : '编辑文件'
  return `${opText}: ${payload.path}`
}

export const useRemoteExecStore = defineStore('remoteExec', () => {
  // === State ===
  const settings = ref<RemoteExecSettings>(loadSettings())
  const status = ref<RemoteExecStatus>('disconnected')
  const pendingCommands = ref<PendingCommand[]>([])
  const executionHistory = ref<Array<{ command: string; result: { exitCode: number; stdout: string; stderr: string; durationMs: number }; timestamp: number }>>([])
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
      // 获取详细平台信息、设备ID、能力集与 Python 候选并发送 client_ready（见开发计划 8.1）
      let platformInfo: string = navigator.platform
      let deviceId = ''
      let pythonCandidates: Array<{ path: string; version: string }> = []
      try {
        const info = await window.electronAPI.app.getPlatformInfo()
        platformInfo = `${info.platform}|${info.arch}|${info.release}`
      } catch { /* fallback to navigator.platform */ }
      try {
        deviceId = await window.electronAPI.projects.getDeviceId()
      } catch { /* 设备ID不可用时留空 */ }
      try {
        pythonCandidates = await window.electronAPI.runtime.listPythonCandidates()
      } catch { /* Python 发现失败时留空 */ }
      sendMessage({
        type: WS_MESSAGE_TYPES.CLIENT_READY,
        requestId: null,
        sessionId: null,
        timestamp: Date.now(),
        payload: {
          platform: platformInfo,
          deviceId,
          version: '2.0',
          capabilities: ['shell', 'python', 'local_files'],
          pythonCandidates
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

      case WS_MESSAGE_TYPES.LOCAL_EXEC_REQUEST:
        handleExecRequest(msg)
        break

      case WS_MESSAGE_TYPES.LOCAL_FS_REQUEST:
        handleLocalFsRequest(msg)
        break

      case WS_MESSAGE_TYPES.RUNTIME_SNAPSHOT_REQUEST:
        handleRuntimeSnapshotRequest(msg)
        break

      case WS_MESSAGE_TYPES.LOCAL_EXEC_CANCEL:
        handleExecCancel(msg)
        break

      case WS_MESSAGE_TYPES.CONNECTED:
        console.log('[RemoteExec] 服务端确认连接')
        break

      default:
        console.warn('[RemoteExec] 未知消息类型:', msg.type)
    }
  }

  // === 统一执行请求 (shell / python) ===

  async function handleExecRequest(msg: WsMessage) {
    const payload = msg.payload as unknown as LocalExecRequestPayload
    if (!payload || !msg.requestId) return

    const pending: PendingCommand = {
      requestId: msg.requestId,
      sessionId: msg.sessionId || '',
      command: summarizeExec(payload),
      kind: payload.kind,
      workingDir: payload.cwd || '',
      riskLevel: payload.riskLevel,
      timeoutMs: payload.timeoutMs,
      receivedAt: Date.now(),
      execPayload: payload
    }

    // 低风险 + 自动执行 → 直接执行
    if (payload.riskLevel === 'LOW' && settings.value.autoExecuteLowRisk) {
      await executeExec(pending)
    } else if (autoApprovedSessions.value.has(pending.sessionId)) {
      // 该会话已授权"全部允许" → 直接执行
      await executeExec(pending)
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

  function handleExecCancel(msg: WsMessage) {
    if (!msg.requestId) return
    pendingCommands.value = pendingCommands.value.filter(c => c.requestId !== msg.requestId)
    // 已在执行中的进程也终止
    window.electronAPI.runtime.cancel(msg.requestId).catch(() => { /* 忽略 */ })
  }

  // === 本地文件 RPC ===

  async function handleLocalFsRequest(msg: WsMessage) {
    const payload = msg.payload as unknown as LocalFsRequestPayload
    if (!payload || !payload.op || !msg.requestId) return

    const isWriteOp = payload.op === 'write' || payload.op === 'edit'
    // 高风险写入（项目外等, 由后端标记）且未自动放行时进入审批队列
    if (isWriteOp && payload.riskLevel === 'HIGH'
        && !autoApprovedSessions.value.has(msg.sessionId || '')) {
      const pending: PendingCommand = {
        requestId: msg.requestId,
        sessionId: msg.sessionId || '',
        command: summarizeFs(payload),
        kind: 'fs',
        workingDir: '',
        riskLevel: 'HIGH',
        timeoutMs: 120_000,
        receivedAt: Date.now(),
        fsPayload: payload
      }
      pendingCommands.value.push(pending)
      setTimeout(() => {
        const still = pendingCommands.value.find(c => c.requestId === pending.requestId)
        if (still) {
          rejectCommand(pending.requestId, 'timeout')
        }
      }, pending.timeoutMs)
      return
    }
    await executeFs(msg.requestId, msg.sessionId, payload)
  }

  async function executeFs(requestId: string, sessionId: string | null, payload: LocalFsRequestPayload) {
    let result: Record<string, unknown>
    try {
      // 剥离 Vue Proxy，确保 IPC structuredClone 可序列化
      const raw = JSON.parse(JSON.stringify(payload)) as LocalFsRequestPayload
      result = await window.electronAPI.runtime.localFs(raw)
    } catch (e) {
      result = { success: false, error: e instanceof Error ? e.message : '本地文件操作失败' }
    }
    sendMessage({
      type: WS_MESSAGE_TYPES.LOCAL_FS_RESULT,
      requestId,
      sessionId,
      timestamp: Date.now(),
      payload: result
    })
  }

  /** 处理定时任务的 runtime snapshot 请求：查本机 ProjectLocation 并回传（见开发计划 8.4） */
  async function handleRuntimeSnapshotRequest(msg: WsMessage) {
    const payload = msg.payload as { projectId?: string } | null
    if (!msg.requestId) return
    const projectId = payload?.projectId || ''
    let snapshot: Record<string, unknown> = { found: false, projectId }
    try {
      snapshot = { ...(await window.electronAPI.projects.getRuntimeSnapshot(projectId)) }
    } catch (e) {
      console.warn('[RemoteExec] 获取 runtime snapshot 失败:', e)
    }
    sendMessage({
      type: WS_MESSAGE_TYPES.RUNTIME_SNAPSHOT_RESULT,
      requestId: msg.requestId,
      sessionId: msg.sessionId,
      timestamp: Date.now(),
      payload: snapshot
    })
  }

  // === 执行 ===

  async function executeExec(cmd: PendingCommand) {
    // 从待审批队列移除
    pendingCommands.value = pendingCommands.value.filter(c => c.requestId !== cmd.requestId)

    if (cmd.kind === 'fs' && cmd.fsPayload) {
      await executeFs(cmd.requestId, cmd.sessionId || null, cmd.fsPayload)
      return
    }

    const payload = cmd.execPayload
    if (!payload) return

    try {
      let pythonExecutable: string | undefined
      if (payload.kind === 'python') {
        // 解释器由本机 ProjectLocation 决定，不接受后端/模型指定
        if (payload.projectId) {
          try {
            const snapshot = await window.electronAPI.projects.getRuntimeSnapshot(payload.projectId)
            pythonExecutable = snapshot.found ? snapshot.pythonExecutable : undefined
          } catch { /* 保持 undefined，由主进程返回错误 */ }
        }
      }

      // payload 来自响应式 ref，用 JSON 深拷贝剥离 Vue Proxy，避免 IPC structuredClone 失败
      const request = JSON.parse(JSON.stringify({
        kind: payload.kind,
        command: payload.command,
        code: payload.code,
        scriptPath: payload.scriptPath,
        args: payload.args,
        cwd: payload.cwd || undefined,
        pythonExecutable,
        limits: {
          timeoutMs: payload.timeoutMs,
          ...payload.resourceLimits
        },
        requestId: cmd.requestId
      }))
      const result = await window.electronAPI.runtime.execute(request)

      // 记录历史
      executionHistory.value.unshift({
        command: cmd.command,
        result: { exitCode: result.exitCode, stdout: result.stdout, stderr: result.stderr, durationMs: result.durationMs },
        timestamp: Date.now()
      })
      if (executionHistory.value.length > 50) executionHistory.value.pop()

      // 回传结果
      sendMessage({
        type: WS_MESSAGE_TYPES.LOCAL_EXEC_RESULT,
        requestId: cmd.requestId,
        sessionId: cmd.sessionId,
        timestamp: Date.now(),
        payload: {
          success: result.success,
          exitCode: result.exitCode,
          stdout: result.stdout,
          stderr: result.stderr,
          durationMs: result.durationMs,
          cancelled: result.cancelled,
          timedOut: result.timedOut
        }
      })
    } catch (e) {
      sendMessage({
        type: WS_MESSAGE_TYPES.LOCAL_EXEC_RESULT,
        requestId: cmd.requestId,
        sessionId: cmd.sessionId,
        timestamp: Date.now(),
        payload: {
          success: false,
          exitCode: -1,
          stdout: '',
          stderr: e instanceof Error ? e.message : '执行失败',
          durationMs: 0,
          cancelled: false,
          timedOut: false
        }
      })
    }
  }

  function rejectCommand(requestId: string, reason: string = 'user_denied') {
    pendingCommands.value = pendingCommands.value.filter(c => c.requestId !== requestId)
    sendMessage({
      type: WS_MESSAGE_TYPES.LOCAL_EXEC_REJECTED,
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
      await executeExec(cmd)
    }
  }

  /** 本次会话全部允许: 标记 sessionId 并立即执行该会话所有 pending 命令 */
  async function approveSession(sessionId: string) {
    autoApprovedSessions.value = new Set([...autoApprovedSessions.value, sessionId])
    // 立即执行该会话所有待审批命令
    const sessionPending = pendingCommands.value.filter(c => c.sessionId === sessionId)
    pendingCommands.value = pendingCommands.value.filter(c => c.sessionId !== sessionId)
    for (const cmd of sessionPending) {
      await executeExec(cmd)
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
