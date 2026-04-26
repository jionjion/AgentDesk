/** 长期记忆设置 */
export interface MemorySettings {
    enabled: boolean
}

/** Mem0 记忆条目 */
export interface MemoryItem {
    id: string
    memory: string
    createdAt: string | null
    updatedAt: string | null
}
