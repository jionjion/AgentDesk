import {contextBridge, ipcRenderer} from 'electron'

/** 执行隔离策略 */
export interface ExecPolicy {
    allowedRoots?: string[]
    resourceLimits?: {
        timeoutMs?: number
        maxOutputChars?: number
        memMB?: number
        maxProcesses?: number
    }
    isolationLevel?: 'boundary'
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
            ipcRenderer.invoke('shell:openExternal', url),
        execute: (command: string, workingDir?: string, policy?: ExecPolicy): Promise<{ exitCode: number; stdout: string; stderr: string; durationMs: number }> =>
            ipcRenderer.invoke('shell:execute', command, workingDir, policy)
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
            ipcRenderer.invoke('projects:touchLocation', projectId)
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
