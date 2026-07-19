import request from './request'
import type {Project, ProjectCreateRequest, ProjectUpdateRequest} from '@/types/project'

/** 创建项目 */
export function createProject(data: ProjectCreateRequest) {
    return request.post<Project>('/api/projects', data)
}

/** 获取所有项目 */
export function getProjects() {
    return request.get<Project[]>('/api/projects')
}

/** 获取项目详情 */
export function getProject(id: string) {
    return request.get<Project>(`/api/projects/${id}`)
}

/** 更新项目 */
export function updateProject(id: string, data: ProjectUpdateRequest) {
    return request.put<Project>(`/api/projects/${id}`, data)
}

/** 删除项目 */
export function deleteProject(id: string) {
    return request.delete(`/api/projects/${id}`)
}

/** 绑定/解绑会话项目 (projectId 为 null 表示解绑) */
export function bindSessionProject(sessionId: string, projectId: string | null) {
    return request.put(`/api/sessions/${sessionId}/project`, {projectId})
}
