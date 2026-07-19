import request from './request'
import type {BackendChatMessage, SearchResult} from '@/types/chat'

const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

/**
 * 获取会话的历史消息
 */
export function getMessages(sessionId: string) {
    return request.get<BackendChatMessage[]>('/api/chat/messages', {
        params: {sessionId}
    })
}

/**
 * 轻量 SSE 包装器，兼容 EventSource 的 addEventListener / close 接口，
 * 但底层使用 fetch（支持 POST + JSON body）。
 */
export interface FetchSSE {
    addEventListener(event: string, handler: (e: MessageEvent) => void): void
    close(): void
    set onerror(handler: (() => void) | null)
}

/** 项目 runtime snapshot（随聊天请求上报, 供后端构造 ProjectRuntimeContext） */
export interface ChatRuntimeSnapshot {
    projectId: string
    deviceId: string
    rootPath: string
    cwd?: string
    platform: string
    pythonExecutable?: string
    pythonVersion?: string
}

export function createChatStream(sessionId: string, message: string, fileIds?: number[], kbIds?: number[], runtimeSnapshot?: ChatRuntimeSnapshot | null): FetchSSE {
    const token = localStorage.getItem('auth_token') || ''
    const url = `${BASE_URL}/api/chat/stream`

    const listeners: Record<string, ((e: MessageEvent) => void)[]> = {}
    let errorHandler: (() => void) | null = null as (() => void) | null
    let abortController: AbortController | null = new AbortController()

    const instance: FetchSSE = {
        addEventListener(event: string, handler: (e: MessageEvent) => void) {
            if (!listeners[event]) listeners[event] = []
            listeners[event].push(handler)
        },
        close() {
            abortController?.abort()
            abortController = null
        },
        set onerror(handler: (() => void) | null) {
            errorHandler = handler
        }
    }

    // 启动 fetch 并解析 SSE 流
    ;(async () => {
        try {
            const body: Record<string, unknown> = {sessionId, message}
            if (fileIds && fileIds.length > 0) body.fileIds = fileIds.join(',')
            if (kbIds && kbIds.length > 0) body.kbIds = kbIds.join(',')
            if (runtimeSnapshot) body.runtimeSnapshot = runtimeSnapshot
            const headers: Record<string, string> = {'Content-Type': 'application/json'}
            if (token) headers['Authorization'] = `Bearer ${token}`

            const response = await fetch(url, {
                method: 'POST',
                headers,
                body: JSON.stringify(body),
                signal: abortController?.signal
            })

            if (!response.ok || !response.body) {
                errorHandler?.()
                return
            }

            const reader = response.body.getReader()
            const decoder = new TextDecoder()
            let buffer = ''
            let currentEvent = 'message'
            let currentData = ''

            while (true) {
                const {done, value} = await reader.read()
                if (done) break

                buffer += decoder.decode(value, {stream: true})
                const lines = buffer.split('\n')
                buffer = lines.pop() || ''

                for (const line of lines) {
                    if (line.startsWith('event:')) {
                        currentEvent = line.slice(6).trim()
                    } else if (line.startsWith('data:')) {
                        currentData = line.slice(5).trim()
                    } else if (line === '') {
                        // 空行表示事件结束，分发事件
                        if (currentData) {
                            const handlers = listeners[currentEvent]
                            if (handlers) {
                                const messageEvent = new MessageEvent(currentEvent, {data: currentData})
                                handlers.forEach(h => h(messageEvent))
                            }
                        }
                        currentEvent = 'message'
                        currentData = ''
                    }
                }
            }
        } catch (e: unknown) {
            if (e instanceof Error && e.name !== 'AbortError') {
                errorHandler?.()
            }
        }
    })()

    return instance
}

/**
 * 中断正在执行的 Agent
 */
export function interruptChat(sessionId: string) {
    return request.post(`/api/chat/${sessionId}/interrupt`)
}

/**
 * 全文搜索消息
 */
export function searchMessages(keyword: string) {
    return request.get<SearchResult[]>('/api/chat/search', {
        params: {keyword}
    })
}

/**
 * 导出会话为 Markdown (后端生成)
 */
export function exportChatMarkdown(sessionId: string) {
    return request.get(`/api/chat/export/${sessionId}`, {
        responseType: 'blob'
    })
}

/**
 * 创建重新生成的 SSE 流连接
 */
export function createRegenerateStream(sessionId: string, messageId: string): EventSource {
    const token = localStorage.getItem('auth_token') || ''
    const url = `${BASE_URL}/api/chat/regenerate?sessionId=${encodeURIComponent(sessionId)}&messageId=${encodeURIComponent(messageId)}&token=${encodeURIComponent(token)}`
    return new EventSource(url)
}
