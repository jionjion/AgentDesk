import request from './request'

export interface FileResponse {
    id: number
    originalName: string
    contentType: string
    size: number
    sessionId: string | null
    downloadUrl: string
    createdAt: number
}

/** 上传文件 */
export function uploadFile(file: File, sessionId?: string) {
    const formData = new FormData()
    formData.append('file', file)
    if (sessionId) {
        formData.append('sessionId', sessionId)
    }
    return request.post<FileResponse>('/api/files/upload', formData, {
        headers: {'Content-Type': 'multipart/form-data'},
        timeout: 60000
    })
}

/** 查询会话下的文件列表 */
export function getSessionFiles(sessionId: string) {
    return request.get<FileResponse[]>(`/api/files/session/${sessionId}`)
}
