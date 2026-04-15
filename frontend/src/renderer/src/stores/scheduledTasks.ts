import {defineStore} from 'pinia'
import {computed, ref} from 'vue'
import type {ScheduledTask, ScheduledTaskFormData, ScheduledTaskLog} from '@/types/scheduledTask'
import {
    createScheduledTask as apiCreate,
    deleteScheduledTask as apiDelete,
    getScheduledTaskLogs,
    getScheduledTasks,
    runScheduledTaskNow as apiRunNow,
    setScheduledTaskEnabled as apiSetEnabled,
    updateScheduledTask as apiUpdate
} from '@/api/scheduledTasks'

export const useScheduledTasksStore = defineStore('scheduledTasks', () => {
    // === State ===
    const tasks = ref<ScheduledTask[]>([])
    const logs = ref<ScheduledTaskLog[]>([])
    const loading = ref(false)
    const logsLoading = ref(false)
    const activeTab = ref<'my-tasks' | 'history'>('my-tasks')
    const sortOrder = ref<'desc' | 'asc'>('desc')

    // === Computed ===

    /** 按创建时间排序后的任务列表 */
    const sortedTasks = computed(() => {
        const list = [...tasks.value]
        return list.sort((a, b) =>
            sortOrder.value === 'desc' ? b.createdAt - a.createdAt : a.createdAt - b.createdAt
        )
    })

    /** 已启用的任务数 */
    const enabledCount = computed(() => tasks.value.filter(t => t.enabled).length)

    // === Actions ===

    /** 加载任务列表 */
    async function fetchTasks() {
        loading.value = true
        try {
            const res = await getScheduledTasks()
            tasks.value = res.data
        } catch (e) {
            console.error('加载定时任务列表失败', e)
        } finally {
            loading.value = false
        }
    }

    /** 加载执行记录 */
    async function fetchLogs() {
        logsLoading.value = true
        try {
            const res = await getScheduledTaskLogs()
            logs.value = res.data
        } catch (e) {
            console.error('加载执行记录失败', e)
        } finally {
            logsLoading.value = false
        }
    }

    /** 创建任务 */
    async function createTask(data: ScheduledTaskFormData) {
        const res = await apiCreate(data)
        tasks.value.unshift(res.data)
        return res.data
    }

    /** 更新任务 */
    async function updateTask(id: number, data: ScheduledTaskFormData) {
        const res = await apiUpdate(id, data)
        const idx = tasks.value.findIndex(t => t.id === id)
        if (idx >= 0) {
            tasks.value[idx] = res.data
        }
        return res.data
    }

    /** 删除任务 */
    async function deleteTask(id: number) {
        await apiDelete(id)
        tasks.value = tasks.value.filter(t => t.id !== id)
    }

    /** 启用/禁用任务 */
    async function toggleEnabled(task: ScheduledTask) {
        const newEnabled = !task.enabled
        await apiSetEnabled(task.id, newEnabled)
        const idx = tasks.value.findIndex(t => t.id === task.id)
        if (idx >= 0) {
            tasks.value[idx] = {...tasks.value[idx], enabled: newEnabled}
        }
    }

    /** 手动执行 */
    async function runNow(task: ScheduledTask) {
        await apiRunNow(task.id)
    }

    return {
        // state
        tasks,
        logs,
        loading,
        logsLoading,
        activeTab,
        sortOrder,
        // computed
        sortedTasks,
        enabledCount,
        // actions
        fetchTasks,
        fetchLogs,
        createTask,
        updateTask,
        deleteTask,
        toggleEnabled,
        runNow
    }
})
