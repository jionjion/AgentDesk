/** 定时任务 */
export interface ScheduledTask {
    id: number
    name: string
    description: string | null
    prompt: string
    cronExpression: string
    scheduleLabel: string | null
    skillId: string | null
    /** 可选关联项目ID */
    projectId: string | null
    /** 目标设备ID (与 projectId 同时保存) */
    deviceId: string | null
    enabled: boolean
    createdAt: number
    updatedAt: number
}

/** 定时任务创建/更新表单 */
export interface ScheduledTaskFormData {
    name: string
    description?: string
    prompt: string
    cronExpression: string
    scheduleLabel?: string
    skillId?: string
    /** 可选关联项目ID */
    projectId?: string
    /** 目标设备ID, 绑定项目时必填 */
    deviceId?: string
}

/** 定时任务执行记录 */
export interface ScheduledTaskLog {
    id: number
    taskId: number
    taskName: string
    status: 'SUCCESS' | 'FAILED' | 'RUNNING'
    result: string | null
    duration: number
    startedAt: number
    endedAt: number | null
}
