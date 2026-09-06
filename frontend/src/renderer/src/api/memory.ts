import request from './request'
import type {AddMemoryPayload, MemoryItem, MemoryJob, MemoryOperations, MemoryPage, MemoryRevision, MemorySettings, MemorySource, MemorySummary, UpdateMemoryPayload} from '@/types/memory'

/** 获取记忆配置 */
export function getMemorySettings() {
    return request.get<MemorySettings>('/api/memory/settings')
}

/** 更新记忆配置 */
export function updateMemorySettings(data: MemorySettings) {
    return request.put<MemorySettings>('/api/memory/settings', data)
}

/** 获取记忆列表 */
export function listMemories(scopeType?: string, scopeId?: string) {
    return request.get<MemoryItem[]>('/api/memory/list', {params: {scopeType, scopeId}})
}

export function listMemoryItems(params: {scopeType?: string; scopeId?: string; category?: string; status?: string; query?: string; page?: number; pageSize?: number}) {
    return request.get<MemoryPage>('/api/memory/items', {params})
}

export function getMemorySummary(scopeType?: string, scopeId?: string) {
    return request.get<MemorySummary>('/api/memory/summary', {params: {scopeType, scopeId}})
}

/** 手动添加记忆 */
export function addMemory(payload: string | AddMemoryPayload) {
    const data = typeof payload === 'string' ? {content: payload} : payload
    return request.post<MemoryItem>('/api/memory', data)
}

/** 删除单条记忆 */
export function deleteMemory(memoryId: string) {
    return request.delete<{ message: string }>(`/api/memory/${memoryId}`)
}

/** 修改一条记忆 */
export function updateMemory(memoryId: string, payload: string | UpdateMemoryPayload) {
    const data = typeof payload === 'string' ? {content: payload} : payload
    return request.put<MemoryItem>(`/api/memory/${memoryId}`, data)
}

/** 清空所有记忆 */
export function deleteAllMemories(scopeType?: string, scopeId?: string) {
    return request.delete<{ message: string; count: number }>('/api/memory/all', {params: {scopeType, scopeId}})
}

export function setMemoryPinned(memoryId: string, pinned: boolean) {
    return request.put<MemoryItem>(`/api/memory/${memoryId}/pin`, {pinned})
}

export function importLegacyMemories() {
    return request.post<{message: string; count: number}>('/api/memory/import-legacy')
}

export function listMemorySources(memoryId: string) {
    return request.get<MemorySource[]>(`/api/memory/${memoryId}/sources`)
}

export function revokeMemorySource(memoryId: string, sourceId: string) {
    return request.delete<{message: string}>(`/api/memory/${memoryId}/sources/${sourceId}`)
}

export function listMemoryRevisions(memoryId: string) {
    return request.get<MemoryRevision[]>(`/api/memory/${memoryId}/revisions`)
}

export function restoreMemoryRevision(memoryId: string, revisionId: string) {
    return request.post<MemoryItem>(`/api/memory/${memoryId}/restore/${revisionId}`)
}

export function resolveMemoryConflict(memoryId: string, action: 'KEEP_NEW' | 'KEEP_OLD') {
    return request.post<MemoryItem>(`/api/memory/conflicts/${memoryId}/resolve`, {action})
}

export function getMemoryOperations() {
    return request.get<MemoryOperations>('/api/memory/operations')
}

export function listMemoryJobs(status?: string) {
    return request.get<MemoryJob[]>('/api/memory/jobs', {params: {status}})
}

export function retryMemoryJob(jobId: number) {
    return request.post<MemoryJob>(`/api/memory/jobs/${jobId}/retry`)
}

export function exportMemories(format: 'markdown' | 'json') {
    return request.get<Blob>('/api/memory/export', {params: {format}, responseType: 'blob'})
}
