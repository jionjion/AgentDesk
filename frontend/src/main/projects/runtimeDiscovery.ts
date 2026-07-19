import {execFile} from 'child_process'
import {promisify} from 'util'
import {access} from 'fs/promises'
import path from 'path'

const execFileAsync = promisify(execFile)

/**
 * Python 解释器发现 (见开发计划 7.1)。
 *
 * 发现顺序:
 * - Windows: 已配置解释器 → <project>/.venv/Scripts/python.exe → py -3 → PATH 中的 python.exe
 * - Unix:    已配置解释器 → <project>/.venv/bin/python → python3 → python
 *
 * 所有候选都会执行 `--version` 验证可用性并取版本号；不可执行的候选被跳过。
 */

export interface PythonCandidate {
    path: string
    version: string
}

/** 验证解释器可执行并返回版本号（如 "3.12.4"），失败返回 null */
export async function probePython(executable: string): Promise<PythonCandidate | null> {
    try {
        const {stdout, stderr} = await execFileAsync(executable, ['--version'], {
            timeout: 5000,
            windowsHide: true,
            env: {...process.env, PYTHONUTF8: '1', PYTHONIOENCODING: 'utf-8'}
        })
        // Python 3.x 输出到 stdout（旧版本到 stderr）
        const output = (stdout || stderr || '').trim()
        const match = output.match(/Python\s+([\d.]+)/i)
        if (!match) return null
        return {path: executable, version: match[1]}
    } catch {
        return null
    }
}

/** 通过 Windows py launcher 解析真实解释器路径 */
async function probePyLauncher(): Promise<PythonCandidate | null> {
    try {
        const {stdout} = await execFileAsync('py', ['-3', '-c', 'import sys; print(sys.executable)'], {
            timeout: 5000,
            windowsHide: true
        })
        const exe = stdout.trim()
        if (!exe) return null
        return probePython(exe)
    } catch {
        return null
    }
}

/** 通过 where/which 在 PATH 中查找 */
async function probeOnPath(name: string): Promise<PythonCandidate | null> {
    const finder = process.platform === 'win32' ? 'where' : 'which'
    try {
        const {stdout} = await execFileAsync(finder, [name], {timeout: 5000, windowsHide: true})
        const first = stdout.split(/\r?\n/).map(s => s.trim()).filter(Boolean)[0]
        if (!first) return null
        // Windows Store 的 python.exe 占位器（WindowsApps）会弹商店而不是执行，跳过
        if (process.platform === 'win32' && first.toLowerCase().includes('windowsapps')) {
            return null
        }
        return probePython(first)
    } catch {
        return null
    }
}

async function fileExists(p: string): Promise<boolean> {
    try {
        await access(p)
        return true
    } catch {
        return false
    }
}

/**
 * 为指定项目发现 Python 解释器。
 *
 * @param projectRoot        项目根目录（用于 .venv 探测），可空
 * @param configuredPython   ProjectLocation 中已配置的解释器路径，可空
 * @returns 首个可用候选，全部失败返回 null
 */
export async function discoverPython(
    projectRoot?: string,
    configuredPython?: string
): Promise<PythonCandidate | null> {
    // 1. 已配置解释器
    if (configuredPython) {
        const candidate = await probePython(configuredPython)
        if (candidate) return candidate
    }
    // 2. 项目 .venv
    if (projectRoot) {
        const venvPython = process.platform === 'win32'
            ? path.join(projectRoot, '.venv', 'Scripts', 'python.exe')
            : path.join(projectRoot, '.venv', 'bin', 'python')
        if (await fileExists(venvPython)) {
            const candidate = await probePython(venvPython)
            if (candidate) return candidate
        }
    }
    // 3. 平台默认
    if (process.platform === 'win32') {
        const fromLauncher = await probePyLauncher()
        if (fromLauncher) return fromLauncher
        return probeOnPath('python.exe')
    }
    const python3 = await probeOnPath('python3')
    if (python3) return python3
    return probeOnPath('python')
}

/**
 * 列出本机可用的 Python 候选（用于 client_ready 上报与设置页展示）。
 * 去重后返回，最多探测系统级候选。
 */
export async function listPythonCandidates(): Promise<PythonCandidate[]> {
    const results: PythonCandidate[] = []
    const seen = new Set<string>()
    const candidates = process.platform === 'win32'
        ? [await probePyLauncher(), await probeOnPath('python.exe')]
        : [await probeOnPath('python3'), await probeOnPath('python')]
    for (const candidate of candidates) {
        if (candidate) {
            const key = process.platform === 'win32' ? candidate.path.toLowerCase() : candidate.path
            if (!seen.has(key)) {
                seen.add(key)
                results.push(candidate)
            }
        }
    }
    return results
}
