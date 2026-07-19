import {spawn} from 'child_process'
import {StringDecoder} from 'string_decoder'
import {createJob} from '../ipc/jobObject'

/**
 * 统一本地进程运行器 (见开发计划 8.2/10.1/10.2)。
 *
 * Shell 与 Python 共用同一套：并发计数、超时 kill 进程树、输出截断、
 * Windows Job Object 资源限制、UTF-8 编码策略。
 * - spawn 环境注入 PYTHONUTF8=1 / PYTHONIOENCODING=utf-8。
 * - stdout/stderr 使用 StringDecoder('utf8')，避免多字节字符跨 chunk 被破坏。
 * - Python 内联代码通过 stdin 写入 `python -`（UTF-8），不拼接 `python -c`。
 */

/** 资源限制 */
export interface RuntimeLimits {
    /** 超时毫秒，超时后 kill 整个进程组 */
    timeoutMs?: number
    /** stdout/stderr 各自的字符上限 */
    maxOutputChars?: number
    /** 进程内存上限（MB），Windows Job Object 硬限制 */
    memMB?: number
    /** 活动进程数上限，Windows Job Object 硬限制 */
    maxProcesses?: number
}

/** 执行结果（统一 shell/python） */
export interface RuntimeResult {
    success: boolean
    exitCode: number
    stdout: string
    stderr: string
    durationMs: number
    cancelled: boolean
    timedOut: boolean
}

/** Python 执行请求 */
export interface PythonExecRequest {
    /** 内联代码（与 scriptPath 二选一），通过 stdin 传给解释器 */
    code?: string
    /** 现有脚本路径（与 code 二选一） */
    scriptPath?: string
    /** 命令行参数 */
    args?: string[]
    /** 工作目录 */
    cwd?: string
    /** 解释器路径（由 ProjectLocation/发现逻辑决定，不接受模型输入） */
    pythonExecutable: string
    limits?: RuntimeLimits
}

const DEFAULT_TIMEOUT_MS = 120_000
const DEFAULT_MAX_OUTPUT_CHARS = 1_000_000
/** 全局并发执行上限（shell 与 python 共用） */
const MAX_CONCURRENT_EXECUTIONS = 5

let runningExecutions = 0

/** requestId -> 取消回调（终止进程组并标记 cancelled） */
const cancellables = new Map<string, () => void>()

/** 按 requestId 取消正在执行的进程（由 local_exec_cancel 触发） */
export function cancelExecution(requestId: string): boolean {
    const cancel = cancellables.get(requestId)
    if (cancel) {
        cancel()
        return true
    }
    return false
}

/** 统一 UTF-8 环境变量（Windows 与中文环境必须显式注入，见开发计划 10.2） */
function buildEnv(extra?: Record<string, string>): NodeJS.ProcessEnv {
    const env: NodeJS.ProcessEnv = {...process.env, ...extra}
    env.PYTHONUTF8 = '1'
    env.PYTHONIOENCODING = 'utf-8'
    if (process.platform === 'win32') {
        env.JAVA_TOOL_OPTIONS = env.JAVA_TOOL_OPTIONS || '-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8'
        env.LESSCHARSET = env.LESSCHARSET || 'utf-8'
    }
    return env
}

/**
 * 执行 shell 命令。Windows 使用 PowerShell 非交互执行，其余平台 /bin/sh -c。
 */
export function executeShell(
    command: string,
    cwd: string | undefined,
    limits?: RuntimeLimits,
    requestId?: string
): Promise<RuntimeResult> {
    const isWindows = process.platform === 'win32'
    let cmd: string
    let args: string[]
    if (isWindows) {
        cmd = 'powershell.exe'
        args = [
            '-NoProfile',
            '-NonInteractive',
            '-Command',
            // 强制输出编码为 UTF-8
            `[Console]::OutputEncoding = [System.Text.Encoding]::UTF8; ${command}`
        ]
    } else {
        cmd = '/bin/sh'
        args = ['-c', command]
    }
    return runProcess({cmd, args, cwd, limits, requestId})
}

/**
 * 执行 Python 代码或脚本。code 通过 stdin 传给 `python -`。
 */
export function executePython(request: PythonExecRequest, requestId?: string): Promise<RuntimeResult> {
    const hasCode = !!request.code && request.code.trim().length > 0
    const args: string[] = ['-X', 'utf8']
    if (hasCode) {
        args.push('-')
    } else {
        args.push(request.scriptPath as string)
    }
    if (request.args && request.args.length > 0) {
        args.push(...request.args)
    }
    return runProcess({
        cmd: request.pythonExecutable,
        args,
        cwd: request.cwd,
        limits: request.limits,
        stdinText: hasCode ? request.code : undefined,
        requestId
    })
}

interface ProcessSpec {
    cmd: string
    args: string[]
    cwd?: string
    limits?: RuntimeLimits
    /** 通过 stdin 写入的文本（UTF-8），写完后关闭 stdin */
    stdinText?: string
    requestId?: string
}

function runProcess(spec: ProcessSpec): Promise<RuntimeResult> {
    const startTime = Date.now()

    if (runningExecutions >= MAX_CONCURRENT_EXECUTIONS) {
        return Promise.resolve({
            success: false,
            exitCode: -1,
            stdout: '',
            stderr: `并发执行数已达上限 (${MAX_CONCURRENT_EXECUTIONS})，请等待当前命令完成。`,
            durationMs: Date.now() - startTime,
            cancelled: false,
            timedOut: false
        })
    }

    runningExecutions++
    return new Promise<RuntimeResult>((resolve) => {
        const isWindows = process.platform === 'win32'
        const timeoutMs = spec.limits?.timeoutMs ?? DEFAULT_TIMEOUT_MS
        const maxOutputChars = spec.limits?.maxOutputChars ?? DEFAULT_MAX_OUTPUT_CHARS

        let child: ReturnType<typeof spawn>
        try {
            child = spawn(spec.cmd, spec.args, {
                cwd: spec.cwd || undefined,
                env: buildEnv(),
                windowsHide: true,
                // 非 Windows 下独立进程组，便于 kill 整组（含子孙进程）
                detached: !isWindows
            })
        } catch (e) {
            resolve({
                success: false,
                exitCode: -1,
                stdout: '',
                stderr: e instanceof Error ? e.message : '启动进程失败',
                durationMs: Date.now() - startTime,
                cancelled: false,
                timedOut: false
            })
            return
        }

        // Windows：把子进程纳入 Job Object，获得内存/进程数硬上限 +
        // KILL_ON_JOB_CLOSE。koffi 不可用时为 null，回退 taskkill /T。
        const job = isWindows && child.pid
            ? createJob({
                memMB: spec.limits?.memMB,
                maxProcesses: spec.limits?.maxProcesses
            })
            : null
        if (job && child.pid) {
            job.assignPid(child.pid)
        }

        // stdin 写入内联代码（显式 UTF-8）
        if (spec.stdinText !== undefined && child.stdin) {
            child.stdin.write(spec.stdinText, 'utf8')
            child.stdin.end()
        } else if (child.stdin) {
            child.stdin.end()
        }

        let stdout = ''
        let stderr = ''
        let stdoutTruncated = false
        let stderrTruncated = false
        let finished = false
        let cancelled = false
        let timedOut = false
        // UTF-8 解码器：多字节字符跨 chunk 时不破坏内容
        const stdoutDecoder = new StringDecoder('utf8')
        const stderrDecoder = new StringDecoder('utf8')

        /** 终止本次执行的整组进程：优先靠 Job Object，否则回退 taskkill/进程组 */
        const terminate = (): void => {
            if (job) {
                job.close()
            } else {
                killProcessTree(child.pid, isWindows)
            }
        }

        const finish = (result: Omit<RuntimeResult, 'durationMs'>): void => {
            if (finished) return
            finished = true
            if (spec.requestId) cancellables.delete(spec.requestId)
            runningExecutions--
            resolve({...result, durationMs: Date.now() - startTime})
        }

        if (spec.requestId) {
            cancellables.set(spec.requestId, () => {
                cancelled = true
                terminate()
            })
        }

        const timer = setTimeout(() => {
            timedOut = true
            terminate()
            finish({
                success: false,
                exitCode: -1,
                stdout,
                stderr: stderr + `\n[执行超时 (${Math.round(timeoutMs / 1000)}秒)，已终止进程组]`,
                cancelled: false,
                timedOut: true
            })
        }, timeoutMs)

        child.stdout?.on('data', (data: Buffer) => {
            if (stdoutTruncated) return
            stdout += stdoutDecoder.write(data)
            if (stdout.length > maxOutputChars) {
                stdout = stdout.slice(0, maxOutputChars) + '\n[输出已截断]'
                stdoutTruncated = true
                terminate()
            }
        })

        child.stderr?.on('data', (data: Buffer) => {
            if (stderrTruncated) return
            stderr += stderrDecoder.write(data)
            if (stderr.length > maxOutputChars) {
                stderr = stderr.slice(0, maxOutputChars) + '\n[错误输出已截断]'
                stderrTruncated = true
            }
        })

        child.on('close', (code) => {
            clearTimeout(timer)
            stdout += stdoutDecoder.end()
            stderr += stderrDecoder.end()
            // 正常结束也关闭 Job 句柄，释放内核对象
            if (job) job.close()
            if (cancelled) {
                finish({
                    success: false,
                    exitCode: -1,
                    stdout,
                    stderr: stderr + '\n[执行已取消]',
                    cancelled: true,
                    timedOut: false
                })
                return
            }
            finish({
                success: (code ?? -1) === 0,
                exitCode: code ?? -1,
                stdout,
                stderr,
                cancelled: false,
                timedOut
            })
        })

        child.on('error', (err) => {
            clearTimeout(timer)
            if (job) job.close()
            finish({
                success: false,
                exitCode: -1,
                stdout: '',
                stderr: err.message,
                cancelled: false,
                timedOut: false
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
