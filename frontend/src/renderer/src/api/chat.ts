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
 * 创建 SSE 流式聊天连接
 */
export function createChatStream(sessionId: string, message: string, fileIds?: number[]): EventSource {
    const token = localStorage.getItem('auth_token') || ''
    let url = `${BASE_URL}/api/chat/stream?sessionId=${encodeURIComponent(sessionId)}&message=${encodeURIComponent(message)}&token=${encodeURIComponent(token)}`
    if (fileIds && fileIds.length > 0) {
        url += `&fileIds=${fileIds.join(',')}`
    }
    return new EventSource(url)
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
