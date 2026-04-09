import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { ChatSession, ChatMessage, AssistantMessage, BackendChatMessage, SSEEventData, Attachment } from '@/types/chat'
import { createSession, getSessions, deleteSession, updateSessionTitle } from '@/api/session'
import { createChatStream, interruptChat, getMessages } from '@/api/chat'
import { uploadFile } from '@/api/file'
import { exportToMarkdown } from '@/utils/exportMarkdown'

const PLAN_TOOL_NAMES = ['create_plan', 'revise_current_plan', 'update_subtask_state', 'finish_subtask', 'view_subtasks', 'finish_plan', 'view_historical_plans', 'recover_historical_plan']

function generateId(): string {
  return Date.now().toString(36) + Math.random().toString(36).substring(2, 8)
}

export const useChatStore = defineStore('chat', () => {
  // === State ===
  const sessions = ref<ChatSession[]>([])
  const currentSessionId = ref<string | null>(null)
  const messagesBySession = ref<Record<string, ChatMessage[]>>({})
  const isStreaming = ref(false)
  const isLoadingSession = ref(false)
  const eventSource = ref<EventSource | null>(null)
  const pinnedSessionIds = ref<Set<string>>(new Set())

  interface PendingFile {
    id: number
    name: string
    size: number
    contentType: string
    uploading: boolean
  }
  const pendingAttachments = ref<PendingFile[]>([])

  // === Computed ===
  const currentSession = computed(() =>
    sessions.value.find(s => s.id === currentSessionId.value) || null
  )

  const currentMessages = computed(() =>
    currentSessionId.value ? (messagesBySession.value[currentSessionId.value] || []) : []
  )

  const sortedSessions = computed(() => {
    const pinned = sessions.value.filter(s => pinnedSessionIds.value.has(s.id))
    const unpinned = sessions.value.filter(s => !pinnedSessionIds.value.has(s.id))
    return [...pinned, ...unpinned]
  })

  // === Actions ===

  /** 加载所有会话 */
  async function loadSessions() {
    try {
      const res = await getSessions()
      sessions.value = res.data
    } catch (e) {
      console.error('加载会话列表失败', e)
    }
  }

  /** 创建新会话 */
  async function createNewSession(title?: string): Promise<string> {
    const res = await createSession(title)
    const session = res.data
    sessions.value.unshift(session)
    currentSessionId.value = session.id
    messagesBySession.value[session.id] = []
    return session.id
  }

  /** 将后端消息映射为前端消息类型 */
  function mapBackendMessage(msg: BackendChatMessage): ChatMessage {
    if (msg.role === 'user') {
      return { id: String(msg.id), role: 'user', content: msg.content, timestamp: msg.createdAt }
    }
    if (msg.role === 'tool') {
      return {
        id: String(msg.id),
        role: 'tool_call',
        toolName: msg.toolName || '',
        toolId: msg.toolId || '',
        arguments: msg.arguments || {},
        result: msg.result,
        status: 'done'
      }
    }
    // assistant
    return {
      id: String(msg.id),
      role: 'assistant',
      content: msg.content,
      timestamp: msg.createdAt,
      isStreaming: false
    }
  }

  /** 切换会话 */
  async function switchSession(id: string) {
    if (isStreaming.value) return
    isLoadingSession.value = true
    currentSessionId.value = id
    try {
      // 已有缓存则直接使用
      if (messagesBySession.value[id]) return
      const res = await getMessages(id)
      messagesBySession.value[id] = res.data.map(mapBackendMessage)
    } catch (e) {
      console.error('加载历史消息失败', e)
      if (!messagesBySession.value[id]) {
        messagesBySession.value[id] = []
      }
    } finally {
      isLoadingSession.value = false
    }
  }

  /** 删除会话 */
  async function removeSession(id: string) {
    try {
      await deleteSession(id)
      sessions.value = sessions.value.filter(s => s.id !== id)
      delete messagesBySession.value[id]
      if (currentSessionId.value === id) {
        currentSessionId.value = sessions.value.length > 0 ? sessions.value[0].id : null
      }
    } catch (e) {
      console.error('删除会话失败', e)
    }
  }

  /** 重命名会话 */
  async function renameSession(id: string, title: string) {
    try {
      await updateSessionTitle(id, title)
      const session = sessions.value.find(s => s.id === id)
      if (session) session.title = title
    } catch (e) {
      console.error('重命名会话失败', e)
    }
  }

  /** 添加附件 (选择文件后调用) */
  async function addAttachment(file: File) {
    if (pendingAttachments.value.length >= 5) return

    const pending: PendingFile = {
      id: 0,
      name: file.name,
      size: file.size,
      contentType: file.type,
      uploading: true
    }
    pendingAttachments.value.push(pending)

    try {
      const res = await uploadFile(file, currentSessionId.value || undefined)
      pending.id = res.data.id
      pending.uploading = false
    } catch (e) {
      pendingAttachments.value = pendingAttachments.value.filter(p => p !== pending)
      console.error('文件上传失败', e)
    }
  }

  /** 移除附件 */
  function removeAttachment(fileId: number) {
    pendingAttachments.value = pendingAttachments.value.filter(p => p.id !== fileId)
  }

  /** 清空附件 */
  function clearAttachments() {
    pendingAttachments.value = []
  }

  /** 发送消息 (核心) */
  async function sendMessage(content: string) {
    const hasAttachments = pendingAttachments.value.some(p => !p.uploading && p.id > 0)
    if ((!content.trim() && !hasAttachments) || isStreaming.value) return

    // 收集附件
    const fileIds = pendingAttachments.value
      .filter(p => !p.uploading && p.id > 0)
      .map(p => p.id)
    const attachments: Attachment[] = pendingAttachments.value
      .filter(p => !p.uploading && p.id > 0)
      .map(p => ({ id: p.id, name: p.name, size: p.size, contentType: p.contentType }))
    clearAttachments()

    const messageContent = content.trim() || '请查看我上传的文件'

    // 1. 自动创建会话
    let sessionId = currentSessionId.value
    if (!sessionId) {
      sessionId = await createNewSession()
    }

    // 2. 推入用户消息
    const messages = messagesBySession.value[sessionId] || []
    messages.push({
      id: generateId(),
      role: 'user',
      content: messageContent,
      timestamp: Date.now(),
      attachments: attachments.length > 0 ? attachments : undefined
    })

    // 3. 推入助手占位消息
    const assistantMsgId = generateId()
    messages.push({
      id: assistantMsgId,
      role: 'assistant',
      content: '',
      timestamp: Date.now(),
      isStreaming: true
    })
    messagesBySession.value[sessionId] = messages

    // 4. 创建 SSE 连接
    isStreaming.value = true
    const es = createChatStream(sessionId, messageContent, fileIds.length > 0 ? fileIds : undefined)
    eventSource.value = es

    // 辅助函数: 获取当前助手消息
    const getAssistantMsg = (): AssistantMessage | undefined => {
      const msgs = messagesBySession.value[sessionId!]
      return msgs?.findLast(m => m.role === 'assistant') as AssistantMessage | undefined
    }

    // 5. 监听事件
    es.addEventListener('text_chunk', (e: MessageEvent) => {
      const data: SSEEventData = JSON.parse(e.data)
      const msg = getAssistantMsg()
      if (msg && data.content) {
        msg.content += data.content
      }
    })

    es.addEventListener('thinking_chunk', (e: MessageEvent) => {
      const data: SSEEventData = JSON.parse(e.data)
      const msgs = messagesBySession.value[sessionId!]
      if (!msgs) return
      // 找到最后一个 thinking 消息或创建新的
      const lastThinking = msgs.findLast(m => m.role === 'thinking' && !m.isCollapsed)
      if (lastThinking && lastThinking.role === 'thinking') {
        lastThinking.content += data.content || ''
      } else {
        // 在助手消息之前插入 thinking
        const assistantIdx = msgs.findLastIndex(m => m.id === assistantMsgId)
        const thinkingMsg: ChatMessage = {
          id: generateId(),
          role: 'thinking',
          content: data.content || '',
          isCollapsed: false
        }
        if (assistantIdx >= 0) {
          msgs.splice(assistantIdx, 0, thinkingMsg)
        } else {
          msgs.push(thinkingMsg)
        }
      }
    })

    es.addEventListener('tool_call_start', (e: MessageEvent) => {
      const data: SSEEventData = JSON.parse(e.data)
      const msgs = messagesBySession.value[sessionId!]
      if (!msgs) return

      const isPlanTool = PLAN_TOOL_NAMES.includes(data.toolName || '')
      const newMsg: ChatMessage = isPlanTool
        ? {
            id: generateId(),
            role: 'plan',
            toolName: data.toolName || '',
            arguments: data.arguments || {},
            result: undefined
          }
        : {
            id: generateId(),
            role: 'tool_call',
            toolName: data.toolName || '',
            toolId: data.toolId || '',
            arguments: data.arguments || {},
            status: 'calling'
          }

      // 在助手消息之前插入
      const assistantIdx = msgs.findLastIndex(m => m.id === assistantMsgId)
      if (assistantIdx >= 0) {
        msgs.splice(assistantIdx, 0, newMsg)
      } else {
        msgs.push(newMsg)
      }
    })

    es.addEventListener('tool_call_end', (e: MessageEvent) => {
      const data: SSEEventData = JSON.parse(e.data)
      const msgs = messagesBySession.value[sessionId!]
      if (!msgs) return

      // 找到对应的 tool_call 或 plan 消息 (匹配 toolName, 从后往前找状态为 calling 的)
      for (let i = msgs.length - 1; i >= 0; i--) {
        const m = msgs[i]
        if (m.role === 'tool_call' && m.toolName === data.toolName && m.status === 'calling') {
          m.result = data.result
          m.status = 'done'
          break
        }
        if (m.role === 'plan' && m.toolName === data.toolName && !m.result) {
          m.result = data.result
          break
        }
      }
    })

    es.addEventListener('reasoning_complete', (e: MessageEvent) => {
      // 折叠当前 thinking 块
      const msgs = messagesBySession.value[sessionId!]
      if (!msgs) return
      const lastThinking = msgs.findLast(m => m.role === 'thinking' && !m.isCollapsed)
      if (lastThinking && lastThinking.role === 'thinking') {
        lastThinking.isCollapsed = true
      }
    })

    es.addEventListener('agent_complete', (_e: MessageEvent) => {
      const msg = getAssistantMsg()
      if (msg) {
        msg.isStreaming = false
      }
      cleanup()
    })

    es.addEventListener('title_generated', (e: MessageEvent) => {
      try {
        const data: SSEEventData = JSON.parse(e.data)
        if (data.content) {
          const session = sessions.value.find(s => s.id === sessionId)
          if (session) session.title = data.content
        }
      } catch {
        // ignore parse errors
      }
    })

    es.addEventListener('error', (e: MessageEvent) => {
      try {
        const data: SSEEventData = JSON.parse(e.data)
        console.error('Agent 错误:', data.error)
        const msg = getAssistantMsg()
        if (msg) {
          msg.content += `\n\n[错误: ${data.error}]`
          msg.isStreaming = false
        }
      } catch {
        // SSE 连接错误 (非 Agent 错误)
        console.error('SSE 连接错误')
      }
      cleanup()
    })

    es.onerror = () => {
      // EventSource 内部错误
      if (isStreaming.value) {
        const msg = getAssistantMsg()
        if (msg && msg.isStreaming) {
          msg.isStreaming = false
          if (!msg.content) {
            msg.content = '[连接中断]'
          }
        }
        cleanup()
      }
    }

    function cleanup() {
      isStreaming.value = false
      es.close()
      eventSource.value = null
    }
  }

  /** 中断 Agent */
  async function interrupt() {
    if (!currentSessionId.value || !isStreaming.value) return
    try {
      await interruptChat(currentSessionId.value)
    } catch (e) {
      console.error('中断失败', e)
    }
    eventSource.value?.close()
    eventSource.value = null
    isStreaming.value = false
    // 标记助手消息结束
    const msgs = messagesBySession.value[currentSessionId.value]
    if (msgs) {
      const lastAssistant = msgs.findLast(m => m.role === 'assistant') as AssistantMessage | undefined
      if (lastAssistant) {
        lastAssistant.isStreaming = false
        if (!lastAssistant.content) {
          lastAssistant.content = '[已中断]'
        }
      }
    }
  }

  /** 切换会话置顶 */
  function togglePin(id: string) {
    if (pinnedSessionIds.value.has(id)) {
      pinnedSessionIds.value.delete(id)
    } else {
      pinnedSessionIds.value.add(id)
    }
  }

  /** 判断会话是否置顶 */
  function isPinned(id: string) {
    return pinnedSessionIds.value.has(id)
  }

  /** 删除单条消息 */
  function deleteMessage(messageId: string) {
    if (!currentSessionId.value) return
    const msgs = messagesBySession.value[currentSessionId.value]
    if (!msgs) return
    const idx = msgs.findIndex(m => m.id === messageId)
    if (idx >= 0) {
      msgs.splice(idx, 1)
    }
  }

  /** 导出会话为 Markdown 文件 */
  async function exportSession(sessionId: string) {
    try {
      const session = sessions.value.find(s => s.id === sessionId)
      if (!session) return

      // 加载消息（不切换当前会话）
      let messages = messagesBySession.value[sessionId]
      if (!messages) {
        const res = await getMessages(sessionId)
        messages = res.data.map(mapBackendMessage)
        messagesBySession.value[sessionId] = messages
      }
      if (messages.length === 0) return

      const markdown = exportToMarkdown(session.title, messages)

      const filePath = await window.electronAPI.dialog.saveFile({
        defaultPath: `${session.title}.md`,
        filters: [{ name: 'Markdown', extensions: ['md'] }]
      })
      if (!filePath) return

      const data = new TextEncoder().encode(markdown)
      await window.electronAPI.fs.writeFile(filePath, data)
    } catch (e) {
      console.error('导出会话失败', e)
    }
  }

  /** 重新生成助手回复 (重发上一条用户消息) */
  async function regenerateMessage(messageId: string) {
    if (!currentSessionId.value || isStreaming.value) return
    const msgs = messagesBySession.value[currentSessionId.value]
    if (!msgs) return

    // 找到该助手消息的位置
    const assistantIdx = msgs.findIndex(m => m.id === messageId)
    if (assistantIdx < 0) return

    // 向前找到最近的用户消息
    let userContent = ''
    for (let i = assistantIdx - 1; i >= 0; i--) {
      if (msgs[i].role === 'user') {
        userContent = (msgs[i] as { content: string }).content
        break
      }
    }
    if (!userContent) return

    // 删除该助手消息及其后续的 thinking/tool_call/plan 消息
    msgs.splice(assistantIdx)

    // 重新发送
    await sendMessage(userContent)
  }

  return {
    // state
    sessions,
    currentSessionId,
    messagesBySession,
    isStreaming,
    isLoadingSession,
    pendingAttachments,
    pinnedSessionIds,
    // computed
    currentSession,
    currentMessages,
    sortedSessions,
    // actions
    loadSessions,
    createNewSession,
    switchSession,
    removeSession,
    renameSession,
    sendMessage,
    interrupt,
    addAttachment,
    removeAttachment,
    clearAttachments,
    togglePin,
    isPinned,
    deleteMessage,
    regenerateMessage,
    exportSession
  }
})
