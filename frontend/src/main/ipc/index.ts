import {app, dialog, ipcMain, shell} from 'electron'
import {readdir, readFile, realpath, stat, writeFile} from 'fs/promises'
import {spawn} from 'child_process'
import os from 'os'
import path from 'path'
import {createJob} from './jobObject'
import {
    bindLocation,
    getDeviceId,
    getLocation,
    getRuntimeSnapshot,
    listLocations,
    removeLocation,
    touchLocation
} from '../projects/projectStore'

/** 执行隔离策略（由渲染进程/后端下发） */
interface ExecPolicy {
    /** 允许执行的根目录树（绝对路径）。必须至少有一个授权根目录 */
    allowedRoots?: string[]
    /** 资源限制 */
    resourceLimits?: {
        /** 超时毫秒，超时后 kill 整个进程组 */
        timeoutMs?: number
        /** stdout/stderr 各自的字符上限 */
        maxOutputChars?: number
        /** 进程内存上限（MB），Windows Job Object 硬限制 */
        memMB?: number
        /** 活动进程数上限，Windows Job Object 硬限制 */
        maxProcesses?: number
    }
    /** 隔离级别：boundary=目录边界+资源兜底 */
    isolationLevel?: 'boundary'
}

/** 默认资源限制 */
const DEFAULT_TIMEOUT_MS = 120_000
const DEFAULT_MAX_OUTPUT_CHARS = 1_000_000
/** 全局并发执行上限 */
const MAX_CONCURRENT_EXECUTIONS = 5
const ALLOWED_EXTERNAL_PROTOCOLS = new Set(['http:', 'https:', 'mailto:'])
let runningExecutions = 0

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

/**
 * 校验工作目录是否落在 allowedRoots 之内。返回钳制后的有效 cwd 或拒绝原因。
 */
async function resolveCwd(workingDir: string | undefined, policy: ExecPolicy | undefined):
    Promise<{ ok: true; cwd: string | undefined } | { ok: false; reason: string }> {
    const roots = policy?.allowedRoots
    if (!roots || roots.length === 0) {
        return {ok: false, reason: '缺少授权工作目录，请先选择工作目录后再执行命令'}
    }
    const canonicalRoots = await Promise.all(roots.map(canonicalize))
    // 未显式指定工作目录时，默认使用第一个授权根
    const targetDir = workingDir && workingDir.trim() ? workingDir : roots[0]
    const canonicalTarget = await canonicalize(targetDir)
    const allowed = canonicalRoots.some(root => isWithin(root, canonicalTarget))
    if (!allowed) {
        return {ok: false, reason: `工作目录 "${targetDir}" 不在授权目录范围内。授权目录: ${roots.join(', ')}`}
    }
    return {ok: true, cwd: canonicalTarget}
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

    // Shell 命令执行（远程执行功能）
    ipcMain.handle('shell:execute', async (_event, command: string, workingDir?: string, policy?: ExecPolicy) => {
        return executeShellCommand(command, workingDir, policy)
    })

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

/**
 * 执行 shell 命令并返回结果（带工作目录边界 + 资源兜底）
 */
async function executeShellCommand(
    command: string,
    workingDir?: string,
    policy?: ExecPolicy
): Promise<{ exitCode: number; stdout: string; stderr: string; durationMs: number }> {
    const startTime = Date.now()

    if (policy?.isolationLevel !== 'boundary') {
        return {
            exitCode: -1,
            stdout: '',
            stderr: '命令被拒绝: 缺少执行隔离策略',
            durationMs: Date.now() - startTime
        }
    }

    // 并发上限
    if (runningExecutions >= MAX_CONCURRENT_EXECUTIONS) {
        return {
            exitCode: -1,
            stdout: '',
            stderr: `并发执行数已达上限 (${MAX_CONCURRENT_EXECUTIONS})，请等待当前命令完成。`,
            durationMs: Date.now() - startTime
        }
    }
    // 工作目录边界
    const cwdResult = await resolveCwd(workingDir, policy)
    if (!cwdResult.ok) {
        return {
            exitCode: -1,
            stdout: '',
            stderr: `命令被拒绝: ${cwdResult.reason}`,
            durationMs: Date.now() - startTime
        }
    }
    workingDir = cwdResult.cwd

    runningExecutions++
    try {
        return await spawnCommand(command, workingDir, policy, startTime)
    } finally {
        runningExecutions--
    }
}

/**
 * 实际 spawn 子进程，应用超时 kill 进程组与输出上限。
 */
function spawnCommand(
    command: string,
    workingDir: string | undefined,
    policy: ExecPolicy | undefined,
    startTime: number
): Promise<{ exitCode: number; stdout: string; stderr: string; durationMs: number }> {
    return new Promise((resolve) => {
        const isWindows = process.platform === 'win32'
        const timeoutMs = policy?.resourceLimits?.timeoutMs ?? DEFAULT_TIMEOUT_MS
        const maxOutputChars = policy?.resourceLimits?.maxOutputChars ?? DEFAULT_MAX_OUTPUT_CHARS

        let shellCmd: string
        let shellArgs: string[]

        if (isWindows) {
            // 使用 PowerShell 执行，原生 UTF-8 支持，中文路径不乱码
            shellCmd = 'powershell.exe'
            shellArgs = [
                '-NoProfile',
                '-NonInteractive',
                '-Command',
                // 强制输出编码为 UTF-8
                `[Console]::OutputEncoding = [System.Text.Encoding]::UTF8; ${command}`
            ]
        } else {
            shellCmd = '/bin/sh'
            shellArgs = ['-c', command]
        }

        // 构建环境变量
        const env = { ...process.env }
        if (isWindows) {
            env.PYTHONIOENCODING = env.PYTHONIOENCODING || 'utf-8'
            env.PYTHONUTF8 = env.PYTHONUTF8 || '1'
            env.JAVA_TOOL_OPTIONS = env.JAVA_TOOL_OPTIONS || '-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8'
            env.LESSCHARSET = env.LESSCHARSET || 'utf-8'
        }

        const child = spawn(shellCmd, shellArgs, {
            cwd: workingDir || undefined,
            env,
            windowsHide: true,
            // 非 Windows 下独立进程组，便于 kill 整组（含子孙进程）
            detached: !isWindows
        })

        // Windows：把子进程纳入 Job Object，获得内存/进程数硬上限 +
        // KILL_ON_JOB_CLOSE（主进程崩溃时自动回收整组）。koffi 不可用时为 null，
        // 回退到 taskkill /T 进程树回收。
        const job = isWindows && child.pid
            ? createJob({
                memMB: policy?.resourceLimits?.memMB,
                maxProcesses: policy?.resourceLimits?.maxProcesses
            })
            : null
        if (job && child.pid) {
            job.assignPid(child.pid)
        }

        let stdout = ''
        let stderr = ''
        let stdoutTruncated = false
        let stderrTruncated = false
        let finished = false

        /** 终止本次执行的整组进程：优先靠 Job Object，否则回退 taskkill/进程组 */
        const terminate = (): void => {
            if (job) {
                job.close()
            } else {
                killProcessTree(child.pid, isWindows)
            }
        }

        const timer = setTimeout(() => {
            terminate()
            if (!finished) {
                finished = true
                resolve({
                    exitCode: -1,
                    stdout,
                    stderr: stderr + `\n[命令执行超时 (${Math.round(timeoutMs / 1000)}秒)，已终止进程组]`,
                    durationMs: Date.now() - startTime
                })
            }
        }, timeoutMs)

        child.stdout.on('data', (data: Buffer) => {
            if (stdoutTruncated) return
            stdout += data.toString()
            if (stdout.length > maxOutputChars) {
                stdout = stdout.slice(0, maxOutputChars) + '\n[输出已截断]'
                stdoutTruncated = true
                terminate()
            }
        })

        child.stderr.on('data', (data: Buffer) => {
            if (stderrTruncated) return
            stderr += data.toString()
            if (stderr.length > maxOutputChars) {
                stderr = stderr.slice(0, maxOutputChars) + '\n[错误输出已截断]'
                stderrTruncated = true
            }
        })

        child.on('close', (code) => {
            clearTimeout(timer)
            // 正常结束也关闭 Job 句柄，释放内核对象
            if (job) job.close()
            if (finished) return
            finished = true
            resolve({
                exitCode: code ?? -1,
                stdout,
                stderr,
                durationMs: Date.now() - startTime
            })
        })

        child.on('error', (err) => {
            clearTimeout(timer)
            if (job) job.close()
            if (finished) return
            finished = true
            resolve({
                exitCode: -1,
                stdout: '',
                stderr: err.message,
                durationMs: Date.now() - startTime
            })
        })
    })
}

/**
 * 终止整个进程树。Windows 用 taskkill /T，类 Unix 用进程组 kill。
 */
function killProcessTree(pid: number | undefined, isWindows: boolean): void {
    if (!pid) return
    try {
        if (isWindows) {
            spawn('taskkill', ['/pid', String(pid), '/T', '/F'], {windowsHide: true})
        } else {
            // detached 时子进程组 id 为 pid，负号表示整组
            process.kill(-pid, 'SIGKILL')
        }
    } catch { /* 进程可能已退出 */ }
}

