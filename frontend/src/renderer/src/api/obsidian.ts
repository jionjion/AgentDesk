import request from './request'

/** 导出单条消息到 Obsidian */
export function exportMessageToObsidian(messageId: number, category?: string) {
    return request.post<{ message: string }>('/api/obsidian/export-message', {messageId, category})
}

/** 导出整个会话到 Obsidian */
export function exportSessionToObsidian(sessionId: string, auto = false) {
    return request.post<{ message: string }>('/api/obsidian/export-session', {sessionId, auto})
}

/** 验证 Vault 路径 */
export function validateVaultPath(path: string) {
    return request.post<{ valid: boolean; message: string }>('/api/obsidian/validate-path', {path})
}

/** 获取已有分类目录 */
export function getObsidianCategories() {
    return request.get<string[]>('/api/obsidian/categories')
}
