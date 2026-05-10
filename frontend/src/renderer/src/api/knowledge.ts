import request from './request'
import type {KnowledgeBase, KnowledgeDocument, RetrievalResult, CreateKnowledgeBaseRequest} from '@/types/knowledge'

// ─── 知识库 ───

export function getKnowledgeBases() {
    return request.get<KnowledgeBase[]>('/api/knowledge/bases')
}

export function createKnowledgeBase(data: CreateKnowledgeBaseRequest) {
    return request.post<KnowledgeBase>('/api/knowledge/bases', data)
}

export function deleteKnowledgeBase(id: number) {
    return request.delete(`/api/knowledge/bases/${id}`)
}

// ─── 文档 ───

export function getDocuments(kbId: number) {
    return request.get<KnowledgeDocument[]>(`/api/knowledge/bases/${kbId}/documents`)
}

export function uploadDocument(kbId: number, file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return request.post<KnowledgeDocument>(`/api/knowledge/bases/${kbId}/documents`, formData, {
        headers: {'Content-Type': 'multipart/form-data'}
    })
}

export function deleteDocument(kbId: number, docId: number) {
    return request.delete(`/api/knowledge/bases/${kbId}/documents/${docId}`)
}

// ─── 检索测试 ───

export function retrieveKnowledge(query: string, topK = 5, scoreThreshold = 0.8) {
    return request.post<RetrievalResult[]>('/api/knowledge/retrieve', {
        query, topK, scoreThreshold
    })
}

// ─── 知识库设置 ───

export interface KnowledgeSettingsDto {
    enabled: boolean
    topK: number
    scoreThreshold: number
}

export function getKnowledgeSettings() {
    return request.get<KnowledgeSettingsDto>('/api/settings/knowledge')
}

export function updateKnowledgeSettings(data: Partial<KnowledgeSettingsDto>) {
    return request.put<KnowledgeSettingsDto>('/api/settings/knowledge', data)
}
