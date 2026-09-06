/** 长期记忆设置 */
export interface MemorySettings {
    schemaVersion: number
    enabled: boolean
    autoLearningEnabled: boolean
    projectMemoryEnabled: boolean
    showMemorySources: boolean
    sensitiveMemoryPolicy: 'EXPLICIT_ONLY'
}

/** Mem0 记忆条目 */
export interface MemoryItem {
    id: string
    memory: string
    createdAt: string | null
    updatedAt: string | null
    scopeType: 'USER' | 'PROJECT'
    scopeId: string | null
    category: string
    status: string
    confidence: number
    importance: number
    pinned: boolean
    sourceCount: number
    recallReason?: string | null
    score?: number
    sensitivity: 'NORMAL' | 'SENSITIVE' | 'SECRET_BLOCKED'
    writePolicy: 'EXPLICIT' | 'AUTO' | 'IMPORT'
    validUntil?: number | null
    supersedesId?: number | null
    subjectKey?: string | null
}

export interface AddMemoryPayload {
    content: string
    scopeType?: 'USER' | 'PROJECT'
    scopeId?: string | null
    category?: string
    validUntil?: number | null
    importance?: number
    sensitiveConfirmed?: boolean
}

export type UpdateMemoryPayload = AddMemoryPayload

export interface MemoryPage {
    items: MemoryItem[]
    total: number
    page: number
    pageSize: number
}

export interface MemorySummary {
    scopeType?: string | null
    scopeId?: string | null
    total: number
    pinned: number
    updatedAt?: number | null
    categoryCounts: Record<string, number>
    highlights: MemoryItem[]
}

export interface MemorySource {
    id: string
    sourceType: string
    sourceId?: string | null
    sessionId?: string | null
    projectId?: string | null
    evidenceExcerpt?: string | null
    trustLevel: string
    createdAt: number
}

export interface MemoryRevision {
    id: string
    oldContent?: string | null
    newContent?: string | null
    reason: string
    actor: string
    createdAt: number
}

export interface MemoryJob {
    id: number
    sessionId: string
    projectId?: string | null
    userMessageId: number
    status: string
    attempts: number
    nextAttemptAt: number
    lastError?: string | null
    createdAt: number
    updatedAt: number
}

export interface MemoryOperations {
    jobCounts: Record<string, number>
    activeMemories: number
    conflictedMemories: number
    expiredMemories: number
    recallEvents: number
    providerCircuitState: 'OPEN' | 'CLOSED'
    providerConsecutiveFailures: number
    providerCircuitOpenUntil: number
}
