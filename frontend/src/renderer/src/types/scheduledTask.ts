/** 定时任务 */
export interface ScheduledTask {
    id: number
    name: string
    description: string | null
    prompt: string
    cronExpression: string
    scheduleLabel: string | null
    skillId: string | null
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
