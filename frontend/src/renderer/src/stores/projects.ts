import {defineStore} from 'pinia'
import {computed, ref} from 'vue'
import type {Project, ProjectLocation} from '@/types/project'
import {
    bindSessionProject as apiBindSessionProject,
    createProject as apiCreateProject,
    deleteProject as apiDeleteProject,
    getProjects,
    updateProject as apiUpdateProject
} from '@/api/projects'

/** 最近使用项目 (新会话默认继承) */
const LAST_PROJECT_KEY = 'last_project_id'

/**
 * 项目 Store: 服务器项目列表 + 当前设备 ProjectLocation。
 * 见 docs/LOCAL_RUNTIME_PROJECT_DEVELOPMENT_PLAN.md 11.1
 */
export const useProjectsStore = defineStore('projects', () => {
    // === State ===
    const projects = ref<Project[]>([])
    const locations = ref<Record<string, ProjectLocation>>({})
    const deviceId = ref('')
    const loading = ref(false)
    const loaded = ref(false)

    // === Computed ===

    /** 项目 id -> 项目 */
    const projectMap = computed(() => {
        const map: Record<string, Project> = {}
        for (const p of projects.value) map[p.id] = p
        return map
    })

    /** 当前设备是否绑定了指定项目的本地位置 */
    function hasLocation(projectId: string): boolean {
        return !!locations.value[projectId]
    }

    /** 最近使用的项目 (本机有位置绑定时优先) */
    const lastProject = computed<Project | null>(() => {
        const lastId = localStorage.getItem(LAST_PROJECT_KEY)
        if (lastId && projectMap.value[lastId]) return projectMap.value[lastId]
        return null
    })

    // === Actions ===

    /** 加载服务器项目列表与本机位置 */
    async function load(): Promise<void> {
        if (loading.value) return
        loading.value = true
        try {
            const [projectsRes, locationList, id] = await Promise.all([
                getProjects(),
                window.electronAPI.projects.listLocations(),
                window.electronAPI.projects.getDeviceId()
            ])
            projects.value = projectsRes.data
            deviceId.value = id
            const map: Record<string, ProjectLocation> = {}
            for (const loc of locationList) map[loc.projectId] = loc
            locations.value = map
            loaded.value = true
        } finally {
            loading.value = false
        }
    }

    /** 创建项目并绑定本机目录 */
    async function createWithLocation(name: string, rootPath: string, description?: string): Promise<Project> {
        const {data: project} = await apiCreateProject({name, description})
        projects.value.unshift(project)
        const location = await window.electronAPI.projects.bindLocation(project.id, rootPath)
        locations.value[project.id] = location
        rememberLastProject(project.id)
        return project
    }

    /** 绑定已有项目到本机目录 */
    async function bindLocation(projectId: string, rootPath: string): Promise<ProjectLocation> {
        const location = await window.electronAPI.projects.bindLocation(projectId, rootPath)
        locations.value[projectId] = location
        return location
    }

    /** 解除项目与本机目录的绑定 */
    async function removeLocation(projectId: string): Promise<void> {
        await window.electronAPI.projects.removeLocation(projectId)
        delete locations.value[projectId]
    }

    /** 更新项目 */
    async function update(id: string, name: string, description?: string, instructions?: string): Promise<void> {
        const {data} = await apiUpdateProject(id, {name, description, instructions})
        const index = projects.value.findIndex(p => p.id === id)
        if (index >= 0) projects.value[index] = data
    }

    /** 删除项目 (服务器会话保留; 本机位置一并清理) */
    async function remove(id: string): Promise<void> {
        await apiDeleteProject(id)
        projects.value = projects.value.filter(p => p.id !== id)
        if (locations.value[id]) {
            await window.electronAPI.projects.removeLocation(id)
            delete locations.value[id]
        }
        if (localStorage.getItem(LAST_PROJECT_KEY) === id) {
            localStorage.removeItem(LAST_PROJECT_KEY)
        }
    }

    /** 绑定/解绑会话项目 */
    async function bindSession(sessionId: string, projectId: string | null): Promise<void> {
        await apiBindSessionProject(sessionId, projectId)
        if (projectId) rememberLastProject(projectId)
    }

    /** 记录最近使用项目 */
    function rememberLastProject(projectId: string): void {
        localStorage.setItem(LAST_PROJECT_KEY, projectId)
        void window.electronAPI.projects.touchLocation(projectId)
    }

    return {
        projects,
        locations,
        deviceId,
        loading,
        loaded,
        projectMap,
        lastProject,
        hasLocation,
        load,
        createWithLocation,
        bindLocation,
        removeLocation,
        update,
        remove,
        bindSession,
        rememberLastProject
    }
})
