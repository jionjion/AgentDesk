import {contextBridge, ipcRenderer} from 'electron'

/** 本地执行资源限制 */
export interface RuntimeLimits {
    timeoutMs?: number
    maxOutputChars?: number
    memMB?: number
    maxProcesses?: number
}

/** 统一本地执行请求 (shell/python) */
export interface LocalExecRequest {
    kind: 'shell' | 'python'
    command?: string
    code?: string
    scriptPath?: string
    args?: string[]
    cwd?: string
    pythonExecutable?: string
    limits?: RuntimeLimits
    requestId?: string
}

/** 统一本地执行结果 */
export interface RuntimeResult {
    success: boolean
    exitCode: number
    stdout: string
    stderr: string
    durationMs: number
    cancelled: boolean
    timedOut: boolean
}

/** 本地文件 RPC 请求 */
export interface LocalFsRequest {
    op: 'read' | 'write' | 'edit' | 'list' | 'glob' | 'grep' | 'stat'
    path: string
    offset?: number
    limit?: number
    content?: string
    oldText?: string
    newText?: string
    replaceAll?: boolean
    expectedReplacements?: number
    pattern?: string
    query?: string
    filePattern?: string
}

/** Python 解释器候选 */
export interface PythonCandidate {
    path: string
    version: string
}

/** 项目在当前设备上的位置配置 */
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


/** 项目 runtime snapshot */
export interface RuntimeSnapshot {
    found: boolean
    projectId: string
    deviceId: string
    rootPath?: string
    cwd?: string
    platform: string
    pythonExecutable?: string
    pythonVersion?: string
}

const electronAPI = {
    dialog: {
        openFile: (): Promise<string[]> => ipcRenderer.invoke('dialog:openFile'),
        saveFile: (options?: { defaultPath?: string; filters?: { name: string; extensions: string[] }[] }): Promise<string> =>
            ipcRenderer.invoke('dialog:saveFile', options),
        openDirectory: (): Promise<string> => ipcRenderer.invoke('dialog:openDirectory')
    },
    fs: {
        readFile: (filePath: string): Promise<Uint8Array> =>
            ipcRenderer.invoke('fs:readFile', filePath),
        writeFile: (filePath: string, data: Uint8Array): Promise<void> =>
            ipcRenderer.invoke('fs:writeFile', filePath, data),
        readDirectory: (dirPath: string): Promise<string[]> =>
            ipcRenderer.invoke('fs:readDirectory', dirPath),
        readDirectoryRecursive: (dirPath: string, exts?: string[]): Promise<string[]> =>
            ipcRenderer.invoke('fs:readDirectoryRecursive', dirPath, exts)
    },
    shell: {
        openExternal: (url: string): Promise<void> =>
            ipcRenderer.invoke('shell:openExternal', url)
    },
    runtime: {
        execute: (request: LocalExecRequest): Promise<RuntimeResult> =>
            ipcRenderer.invoke('runtime:execute', request),
        cancel: (requestId: string): Promise<boolean> =>
            ipcRenderer.invoke('runtime:cancel', requestId),
        localFs: (request: LocalFsRequest): Promise<Record<string, unknown>> =>
            ipcRenderer.invoke('runtime:localFs', request),
        listPythonCandidates: (): Promise<PythonCandidate[]> =>
            ipcRenderer.invoke('runtime:listPythonCandidates')
    },
    app: {
        getVersion: (): Promise<string> => ipcRenderer.invoke('app:getVersion'),
        getPlatformInfo: (): Promise<{ platform: string; arch: string; release: string; hostname: string }> =>
            ipcRenderer.invoke('system:getPlatformInfo'),
        getCloseAction: (): Promise<'ask' | 'minimize' | 'quit'> =>
            ipcRenderer.invoke('app:getCloseAction'),
        setCloseAction: (action: 'ask' | 'minimize' | 'quit'): Promise<void> =>
            ipcRenderer.invoke('app:setCloseAction', action),
        confirmClose: (choice: 'quit' | 'minimize'): void =>
            ipcRenderer.send('app:confirmClose', choice),
        onShowCloseDialog: (callback: () => void): void => {
            ipcRenderer.on('show-close-dialog', callback)
        },
        offShowCloseDialog: (): void => {
            ipcRenderer.removeAllListeners('show-close-dialog')
        },
        getAutoLaunch: (): Promise<boolean> =>
            ipcRenderer.invoke('app:getAutoLaunch'),
        setAutoLaunch: (enabled: boolean): Promise<void> =>
            ipcRenderer.invoke('app:setAutoLaunch', enabled)
    },
    window: {
        minimize: (): void => ipcRenderer.send('window:minimize'),
        maximize: (): void => ipcRenderer.send('window:maximize'),
        close: (): void => ipcRenderer.send('window:close')
    },
    projects: {
        getDeviceId: (): Promise<string> => ipcRenderer.invoke('projects:getDeviceId'),
        listLocations: (): Promise<ProjectLocation[]> => ipcRenderer.invoke('projects:listLocations'),
        getLocation: (projectId: string): Promise<ProjectLocation | null> =>
            ipcRenderer.invoke('projects:getLocation', projectId),
        bindLocation: (projectId: string, rootPath: string): Promise<ProjectLocation> =>
            ipcRenderer.invoke('projects:bindLocation', projectId, rootPath),
        removeLocation: (projectId: string): Promise<boolean> =>
            ipcRenderer.invoke('projects:removeLocation', projectId),
        touchLocation: (projectId: string): Promise<void> =>
            ipcRenderer.invoke('projects:touchLocation', projectId),
        getRuntimeSnapshot: (projectId: string): Promise<RuntimeSnapshot> =>
            ipcRenderer.invoke('projects:getRuntimeSnapshot', projectId)
    },
    updater: {
        check: (): Promise<unknown> => ipcRenderer.invoke('updater:check'),
        download: (): Promise<unknown> => ipcRenderer.invoke('updater:download'),
        install: (): void => { ipcRenderer.invoke('updater:install') },
        onChecking: (callback: () => void): void => {
            ipcRenderer.on('updater:checking', callback)
        },
        onAvailable: (callback: (_event: unknown, info: { version: string; releaseDate: string; releaseNotes: string }) => void): void => {
            ipcRenderer.on('updater:available', callback)
        },
        onNotAvailable: (callback: () => void): void => {
            ipcRenderer.on('updater:not-available', callback)
        },
        onProgress: (callback: (_event: unknown, progress: { percent: number; transferred: number; total: number; bytesPerSecond: number }) => void): void => {
            ipcRenderer.on('updater:progress', callback)
        },
        onDownloaded: (callback: () => void): void => {
            ipcRenderer.on('updater:downloaded', callback)
        },
        onError: (callback: (_event: unknown, message: string) => void): void => {
            ipcRenderer.on('updater:error', callback)
        },
        removeAllListeners: (): void => {
            ipcRenderer.removeAllListeners('updater:checking')
            ipcRenderer.removeAllListeners('updater:available')
            ipcRenderer.removeAllListeners('updater:not-available')
            ipcRenderer.removeAllListeners('updater:progress')
            ipcRenderer.removeAllListeners('updater:downloaded')
            ipcRenderer.removeAllListeners('updater:error')
        }
    }
}

contextBridge.exposeInMainWorld('electronAPI', electronAPI)

export type ElectronAPI = typeof electronAPI
