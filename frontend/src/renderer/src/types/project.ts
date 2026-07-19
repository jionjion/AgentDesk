/** 服务器项目实体 */
export interface Project {
    id: string
    name: string
    description: string | null
    instructions: string | null
    createdAt: number
    updatedAt: number
}

/** 创建项目请求 */
export interface ProjectCreateRequest {
    name: string
    description?: string
    instructions?: string
}

/** 更新项目请求 */
export interface ProjectUpdateRequest {
    name: string
    description?: string
    instructions?: string
}

/** 项目在当前设备上的位置配置（来自 Electron 主进程） */
export interface ProjectLocation {
    projectId: string
    deviceId: string
    rootPath: string
    defaultCwd?: string
    pythonExecutable?: string
    shellProfile?: 'powershell' | 'cmd' | 'sh' | 'bash' | 'zsh'
    env?: Record<string, string>
    lastOpenedAt: number
}
