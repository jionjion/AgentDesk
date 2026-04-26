import request from './request'
import type {MemoryItem, MemorySettings} from '@/types/memory'

/** 获取记忆配置 */
export function getMemorySettings() {
    return request.get<MemorySettings>('/api/memory/settings')
}

/** 更新记忆配置 */
export function updateMemorySettings(data: MemorySettings) {
    return request.put<MemorySettings>('/api/memory/settings', data)
}

/** 获取记忆列表 */
export function listMemories() {
    return request.get<MemoryItem[]>('/api/memory/list')
}

/** 手动添加记忆 */
export function addMemory(content: string) {
    return request.post<{ message: string }>('/api/memory', {content})
}

/** 删除单条记忆 */
export function deleteMemory(memoryId: string) {
    return request.delete<{ message: string }>(`/api/memory/${memoryId}`)
}

/** 修改一条记忆 */
export function updateMemory(memoryId: string, content: string) {
    return request.put<{ message: string }>(`/api/memory/${memoryId}`, {content})
}

/** 清空所有记忆 */
export function deleteAllMemories() {
    return request.delete<{ message: string }>('/api/memory/all')
}
