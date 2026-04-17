import request from './request'
import type {ChatSession} from '@/types/chat'

/** 创建会话 */
export function createSession(title?: string) {
    return request.post<ChatSession>('/api/sessions', {title: title || '新对话'})
}

/** 获取所有会话 */
export function getSessions() {
    return request.get<ChatSession[]>('/api/sessions')
}

/** 获取会话详情 */
export function getSession(id: string) {
    return request.get<ChatSession>(`/api/sessions/${id}`)
}

/** 删除会话 */
export function deleteSession(id: string) {
    return request.delete(`/api/sessions/${id}`)
}

/** 批量删除会话 */
export function batchDeleteSessions(ids: string[]) {
    return request.delete('/api/sessions/batch', {data: ids})
}

/** 更新会话标题 */
export function updateSessionTitle(id: string, title: string) {
    return request.put<ChatSession>(`/api/sessions/${id}`, {title})
}
