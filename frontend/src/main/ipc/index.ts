import {app, dialog, ipcMain, shell} from 'electron'
import {readdir, readFile, realpath, stat, writeFile} from 'fs/promises'
import os from 'os'
import path from 'path'
import {cancelExecution, executePython, executeShell, RuntimeLimits, RuntimeResult} from '../runtime/localRuntime'
import {handleLocalFs, LocalFsRequest} from '../runtime/localFs'
import {listPythonCandidates} from '../projects/runtimeDiscovery'
import {
    bindLocation,
    getDeviceId,
    getLocation,
    getRuntimeSnapshot,
    listLocations,
    removeLocation,
    touchLocation
} from '../projects/projectStore'

/** 本地执行请求（渲染进程转发后端 local_exec_request payload） */
interface LocalExecRequest {
    kind: 'shell' | 'python'
    command?: string
    code?: string
    scriptPath?: string
    args?: string[]
    cwd?: string
    /** Python 解释器路径（渲染进程从 ProjectLocation snapshot 获取） */
    pythonExecutable?: string
    limits?: RuntimeLimits
    /** 用于取消的请求 ID */
    requestId?: string
}

const ALLOWED_EXTERNAL_PROTOCOLS = new Set(['http:', 'https:', 'mailto:'])

const allowedReadFiles = new Set<string>()
const allowedReadRoots = new Set<string>()
const allowedWriteFiles = new Set<string>()

function isAllowedExternalUrl(urlString: string): boolean {
    try {
        return ALLOWED_EXTERNAL_PROTOCOLS.has(new URL(urlString).protocol)
    } catch {
        return false
    }
}

/**
 * 规范化路径：解析符号链接后取绝对路径。路径不存在时回退到 path.resolve。
 */
async function canonicalize(p: string): Promise<string> {
    const resolved = path.resolve(p)
    try {
        return await realpath(resolved)
    } catch {
        return resolved
    }
}

function pathKey(p: string): string {
    return process.platform === 'win32' ? p.toLowerCase() : p
}

async function rememberReadFile(filePath: string): Promise<void> {
    allowedReadFiles.add(pathKey(await canonicalize(filePath)))
}

async function rememberReadRoot(dirPath: string): Promise<void> {
    allowedReadRoots.add(pathKey(await canonicalize(dirPath)))
}

async function rememberWriteFile(filePath: string): Promise<void> {
    allowedWriteFiles.add(pathKey(await canonicalize(filePath)))
}

/** 判断 child 是否在 root 目录树之内（含相等） */
function isWithin(root: string, child: string): boolean {
    const a = pathKey(root.replace(/[\\/]+$/, ''))
    const b = pathKey(child.replace(/[\\/]+$/, ''))
    if (a === b) return true
    const sep = process.platform === 'win32' ? '\\' : '/'
    return b.startsWith(a + sep) || b.startsWith(a + '/')
}

async function assertReadAllowed(filePath: string): Promise<string> {
    const canonical = await canonicalize(filePath)
    const key = pathKey(canonical)
    if (allowedReadFiles.has(key)) {
        return canonical
    }
    for (const root of allowedReadRoots) {
        if (isWithin(root, key)) {
            return canonical
        }
    }
    throw new Error('未授权读取该路径')
}

async function assertReadRootAllowed(dirPath: string): Promise<string> {
    const canonical = await canonicalize(dirPath)
    const key = pathKey(canonical)
    for (const root of allowedReadRoots) {
        if (isWithin(root, key)) {
            return canonical
        }
    }
    throw new Error('未授权读取该目录')
}

async function assertWriteAllowed(filePath: string): Promise<string> {
    const canonical = await canonicalize(filePath)
    if (!allowedWriteFiles.has(pathKey(canonical))) {
        throw new Error('未授权写入该路径')
    }
    return canonical
}

export function registerIpcHandlers(): void {
    // 文件对话框
    ipcMain.handle('dialog:openFile', async () => {
        const result = await dialog.showOpenDialog({
            properties: ['openFile', 'multiSelections']
        })
        if (result.canceled) return []
        await Promise.all(result.filePaths.map(rememberReadFile))
        return result.filePaths
    })

    ipcMain.handle('dialog:saveFile', async (_event, options?: { defaultPath?: string; filters?: { name: string; extensions: string[] }[] }) => {
        const result = await dialog.showSaveDialog({
            defaultPath: options?.defaultPath,
            filters: options?.filters
        })
        if (result.canceled || !result.filePath) return ''
        await rememberWriteFile(result.filePath)
        return result.filePath
    })

    ipcMain.handle('dialog:openDirectory', async () => {
        const result = await dialog.showOpenDialog({properties: ['openDirectory']})
        if (result.canceled || !result.filePaths[0]) return ''
        await rememberReadRoot(result.filePaths[0])
        return result.filePaths[0]
    })

    // 文件系统操作
    ipcMain.handle('fs:readFile', async (_event, filePath: string) => {
        const allowedPath = await assertReadAllowed(filePath)
        const data = await readFile(allowedPath)
        return new Uint8Array(data)
    })

    ipcMain.handle('fs:writeFile', async (_event, filePath: string, data: Uint8Array) => {
        const allowedPath = await assertWriteAllowed(filePath)
        await writeFile(allowedPath, data)
    })

    ipcMain.handle('fs:readDirectory', async (_event, dirPath: string) => {
        const allowedPath = await assertReadRootAllowed(dirPath)
        const entries = await readdir(allowedPath)
        return entries
    })

    // 递归读取目录，返回相对路径列表（仅文件）
    ipcMain.handle('fs:readDirectoryRecursive', async (_event, dirPath: string, exts?: string[]) => {
        const results: string[] = []
        const MAX_FILES = 200
        const MAX_DEPTH = 5
        const allowedDir = await assertReadRootAllowed(dirPath)

        async function walk(currentDir: string, relativePath: string, depth: number) {
            if (depth > MAX_DEPTH || results.length >= MAX_FILES) return
            const entries = await readdir(currentDir)
            for (const entry of entries) {
                if (results.length >= MAX_FILES) break
                if (entry.startsWith('.')) continue // 跳过隐藏文件/目录
                const fullPath = path.join(currentDir, entry)
                const relPath = relativePath ? `${relativePath}/${entry}` : entry
                try {
                    const s = await stat(fullPath)
                    if (s.isDirectory()) {
                        await walk(fullPath, relPath, depth + 1)
                    } else if (s.isFile()) {
                        if (exts && exts.length > 0) {
                            const ext = path.extname(entry).toLowerCase()
                            if (!exts.includes(ext)) continue
                        }
                        results.push(relPath)
                    }
                } catch { /* 跳过无权限文件 */ }
            }
        }

        await walk(allowedDir, '', 0)
        return results
    })

    // Shell 操作
    ipcMain.handle('shell:openExternal', async (_event, url: string) => {
        if (!isAllowedExternalUrl(url)) {
            throw new Error('不允许打开该链接')
        }
        await shell.openExternal(url)
    })

    // 本地执行（统一 shell/python 运行器, 见开发计划 8.2/10.x）
    ipcMain.handle('runtime:execute', async (_event, request: LocalExecRequest): Promise<RuntimeResult> => {
        if (request.kind === 'python') {
            if (!request.pythonExecutable) {
                return {
                    success: false, exitCode: -1, stdout: '',
                    stderr: '缺少 Python 解释器路径, 请先在项目中配置或创建 .venv',
                    durationMs: 0, cancelled: false, timedOut: false
                }
            }
            return executePython({
                code: request.code,
                scriptPath: request.scriptPath,
                args: request.args,
                cwd: request.cwd,
                pythonExecutable: request.pythonExecutable,
                limits: request.limits
            }, request.requestId)
        }
        if (!request.command) {
            return {
                success: false, exitCode: -1, stdout: '', stderr: '命令不能为空',
                durationMs: 0, cancelled: false, timedOut: false
            }
        }
        return executeShell(request.command, request.cwd, request.limits, request.requestId)
    })

    // 取消正在执行的本地进程
    ipcMain.handle('runtime:cancel', (_event, requestId: string) => cancelExecution(requestId))

    // 本地文件 RPC（见开发计划 8.3）
    ipcMain.handle('runtime:localFs', (_event, request: LocalFsRequest) => handleLocalFs(request))

    // Python 候选发现（设置页/client_ready 上报）
    ipcMain.handle('runtime:listPythonCandidates', () => listPythonCandidates())

    // 应用信息
    ipcMain.handle('app:getVersion', () => {
        return app.getVersion()
    })

    // 系统平台信息（供远程执行上报）
    ipcMain.handle('system:getPlatformInfo', () => {
        return {
            platform: process.platform,       // 'win32' | 'darwin' | 'linux'
            arch: process.arch,               // 'x64' | 'arm64' 等
            release: os.release(),            // 如 '10.0.26200'
            hostname: os.hostname()
        }
    })

    // 项目本地位置管理（设备级配置, 见开发计划第 7 章）
    ipcMain.handle('projects:getDeviceId', () => getDeviceId())

    ipcMain.handle('projects:listLocations', () => listLocations())

    ipcMain.handle('projects:getLocation', (_event, projectId: string) => getLocation(projectId))

    ipcMain.handle('projects:bindLocation', async (_event, projectId: string, rootPath: string) => {
        const location = await bindLocation(projectId, rootPath)
        // 绑定的项目根目录同时授权为可读根, 供现有文件读取 IPC 使用
        await rememberReadRoot(location.rootPath)
        return location
    })

    ipcMain.handle('projects:removeLocation', (_event, projectId: string) => removeLocation(projectId))

    ipcMain.handle('projects:touchLocation', (_event, projectId: string) => touchLocation(projectId))

    ipcMain.handle('projects:getRuntimeSnapshot', (_event, projectId: string) => getRuntimeSnapshot(projectId))
}
