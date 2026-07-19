import {app} from 'electron'
import {randomUUID} from 'crypto'
import {mkdir, readFile, rename, writeFile} from 'fs/promises'
import {realpathSync} from 'fs'
import path from 'path'

/**
 * 项目在当前 PC 上的位置与解释器配置（设备级数据, 不进入服务器数据库）。
 * 见 docs/LOCAL_RUNTIME_PROJECT_DEVELOPMENT_PLAN.md 5.2
 */
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

interface ProjectStoreFile {
    /** 设备唯一 ID, 首次启动生成并持久化 */
    deviceId: string
    /** projectId -> ProjectLocation */
    locations: Record<string, ProjectLocation>
}

let cache: ProjectStoreFile | null = null

function storeDir(): string {
    return path.join(app.getPath('userData'), 'local-settings')
}

function storeFilePath(): string {
    return path.join(storeDir(), 'projects.json')
}

/** 规范化路径: 解析符号链接后取绝对路径, 不存在时回退 path.resolve */
function canonicalize(p: string): string {
    const resolved = path.resolve(p)
    try {
        return realpathSync(resolved)
    } catch {
        return resolved
    }
}

async function load(): Promise<ProjectStoreFile> {
    if (cache) return cache
    try {
        const raw = await readFile(storeFilePath(), 'utf-8')
        const parsed = JSON.parse(raw) as Partial<ProjectStoreFile>
        if (parsed.deviceId && typeof parsed.deviceId === 'string') {
            cache = {
                deviceId: parsed.deviceId,
                locations: parsed.locations ?? {}
            }
            return cache
        }
    } catch { /* 文件不存在或损坏, 重新初始化 */ }
    cache = {deviceId: randomUUID(), locations: {}}
    await persist(cache)
    return cache
}

/** 同目录临时文件 + 原子替换写入, 避免半写文件 */
async function persist(data: ProjectStoreFile): Promise<void> {
    await mkdir(storeDir(), {recursive: true})
    const target = storeFilePath()
    const tmp = target + '.tmp'
    await writeFile(tmp, JSON.stringify(data, null, 2), 'utf-8')
    await rename(tmp, target)
}

/** 获取设备唯一 ID */
export async function getDeviceId(): Promise<string> {
    return (await load()).deviceId
}

/** 列出本机所有项目位置 */
export async function listLocations(): Promise<ProjectLocation[]> {
    return Object.values((await load()).locations)
}

/** 获取指定项目在本机的位置 */
export async function getLocation(projectId: string): Promise<ProjectLocation | null> {
    return (await load()).locations[projectId] ?? null
}

/** 绑定项目到本机目录。路径写入前规范化。 */
export async function bindLocation(
    projectId: string,
    rootPath: string,
    options?: Pick<ProjectLocation, 'defaultCwd' | 'pythonExecutable' | 'shellProfile'>
): Promise<ProjectLocation> {
    if (!projectId || !rootPath) {
        throw new Error('projectId 和 rootPath 不能为空')
    }
    const store = await load()
    const location: ProjectLocation = {
        projectId,
        deviceId: store.deviceId,
        rootPath: canonicalize(rootPath),
        defaultCwd: options?.defaultCwd,
        pythonExecutable: options?.pythonExecutable,
        shellProfile: options?.shellProfile,
        lastOpenedAt: Date.now()
    }
    store.locations[projectId] = location
    await persist(store)
    return location
}

/** 解除项目与本机目录的绑定 */
export async function removeLocation(projectId: string): Promise<boolean> {
    const store = await load()
    if (!store.locations[projectId]) return false
    delete store.locations[projectId]
    await persist(store)
    return true
}

/** 更新最近打开时间 */
export async function touchLocation(projectId: string): Promise<void> {
    const store = await load()
    const location = store.locations[projectId]
    if (location) {
        location.lastOpenedAt = Date.now()
        await persist(store)
    }
}
