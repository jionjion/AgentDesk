/** 知识库 */
export interface KnowledgeBase {
    id: number
    name: string
    description?: string
    docCount: number
    chunkCount: number
    status: 'active' | 'archived'
    createdAt: number
    updatedAt: number
}

/** 知识文档 */
export interface KnowledgeDocument {
    id: number
    fileName: string
    fileSize: number
    contentType?: string
    charCount: number
    chunkCount: number
    status: 'pending' | 'processing' | 'done' | 'failed'
    errorMessage?: string
    createdAt: number
}

/** 检索结果 */
export interface RetrievalResult {
    chunkId: number
    content: string
    score: number
    documentName: string
    chunkIndex: number
}

/** 创建知识库请求 */
export interface CreateKnowledgeBaseRequest {
    name: string
    description?: string
}
