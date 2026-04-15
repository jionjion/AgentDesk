import request from './request'
import type {ScheduledTask, ScheduledTaskFormData, ScheduledTaskLog} from '@/types/scheduledTask'

/** 列出用户的所有定时任务 */
export function getScheduledTasks() {
    return request.get<ScheduledTask[]>('/api/scheduled-tasks')
}

/** 获取单个定时任务详情 */
export function getScheduledTask(id: number) {
    return request.get<ScheduledTask>(`/api/scheduled-tasks/${id}`)
}

/** 创建定时任务 */
export function createScheduledTask(data: ScheduledTaskFormData) {
    return request.post<ScheduledTask>('/api/scheduled-tasks', data)
}

/** 更新定时任务 */
export function updateScheduledTask(id: number, data: ScheduledTaskFormData) {
    return request.put<ScheduledTask>(`/api/scheduled-tasks/${id}`, data)
}

/** 删除定时任务 */
export function deleteScheduledTask(id: number) {
    return request.delete(`/api/scheduled-tasks/${id}`)
}

/** 启用/禁用定时任务 */
export function setScheduledTaskEnabled(id: number, enabled: boolean) {
    return request.put<{ message: string }>(`/api/scheduled-tasks/${id}/enabled`, {enabled})
}

/** 手动立即执行 */
export function runScheduledTaskNow(id: number) {
    return request.post<{ message: string }>(`/api/scheduled-tasks/${id}/run`)
}

/** 获取用户全部执行记录 */
export function getScheduledTaskLogs() {
    return request.get<ScheduledTaskLog[]>('/api/scheduled-tasks/logs')
}

/** 获取单个任务的执行记录 */
export function getTaskLogs(taskId: number) {
    return request.get<ScheduledTaskLog[]>(`/api/scheduled-tasks/${taskId}/logs`)
}
