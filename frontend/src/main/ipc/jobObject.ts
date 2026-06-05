/**
 * Windows Job Object 封装（koffi FFI）。
 *
 * 作用：把 spawn 出来的子进程及其子孙进程纳入一个 Job Object，从而：
 * - 设置内存 / 活动进程数硬上限（taskkill 做不到）；
 * - 启用 KILL_ON_JOB_CLOSE：句柄关闭（含主进程崩溃）时自动回收整组进程，
 *   避免残留 PowerShell 子孙进程。
 *
 * 仅在 Windows 生效；其它平台所有方法为 no-op，由调用方走 detached 进程组方案。
 * koffi 加载失败时静默降级（返回 null），不影响命令执行。
 */

let koffi: typeof import('koffi') | null = null
let kernel32: ReturnType<typeof import('koffi')['load']> | null = null
let loadAttempted = false

// Win32 常量
const JobObjectExtendedLimitInformation = 9
const JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE = 0x00002000
const JOB_OBJECT_LIMIT_PROCESS_MEMORY = 0x00000100
const JOB_OBJECT_LIMIT_ACTIVE_PROCESS = 0x00000008
const PROCESS_ALL_ACCESS = 0x1f0fff

// 延迟解析的 koffi 函数 / 结构体
interface JobApi {
    createJobObject: () => unknown
    setInformation: (hJob: unknown, buf: Buffer) => boolean
    openProcess: (pid: number) => unknown
    assign: (hJob: unknown, hProc: unknown) => boolean
    closeHandle: (h: unknown) => boolean
    extLimitInfo: unknown
    sizeofExtLimitInfo: number
    encode: (buf: Buffer, type: unknown, obj: unknown) => void
    isNull: (h: unknown) => boolean
}

let jobApi: JobApi | null = null

/** Job Object 资源限制配置 */
export interface JobLimits {
    /** 进程内存上限（MB），<=0 或省略表示不限 */
    memMB?: number
    /** 活动进程数上限，<=0 或省略表示不限 */
    maxProcesses?: number
}

/** 已创建的 Job 句柄包装，调用 close() 触发 KILL_ON_JOB_CLOSE */
export interface JobHandle {
    /** 把子进程（及其后代）纳入本 Job */
    assignPid: (pid: number) => boolean
    /** 关闭 Job 句柄；KILL_ON_JOB_CLOSE 会回收整组进程 */
    close: () => void
}

function ensureLoaded(): JobApi | null {
    if (jobApi) return jobApi
    if (loadAttempted) return null
    loadAttempted = true
    if (process.platform !== 'win32') return null

    try {
        // eslint-disable-next-line @typescript-eslint/no-require-imports
        koffi = require('koffi')
        kernel32 = koffi!.load('kernel32.dll')

        const HANDLE = koffi!.pointer('HANDLE', koffi!.opaque())

        const IO_COUNTERS = koffi!.struct('IO_COUNTERS', {
            ReadOperationCount: 'uint64',
            WriteOperationCount: 'uint64',
            OtherOperationCount: 'uint64',
            ReadTransferCount: 'uint64',
            WriteTransferCount: 'uint64',
            OtherTransferCount: 'uint64'
        })

        const JOBOBJECT_BASIC_LIMIT_INFORMATION = koffi!.struct('JOBOBJECT_BASIC_LIMIT_INFORMATION', {
            PerProcessUserTimeLimit: 'int64',
            PerJobUserTimeLimit: 'int64',
            LimitFlags: 'uint32',
            MinimumWorkingSetSize: 'size_t',
            MaximumWorkingSetSize: 'size_t',
            ActiveProcessLimit: 'uint32',
            Affinity: 'uintptr_t',
            PriorityClass: 'uint32',
            SchedulingClass: 'uint32'
        })

        const JOBOBJECT_EXTENDED_LIMIT_INFORMATION = koffi!.struct('JOBOBJECT_EXTENDED_LIMIT_INFORMATION', {
            BasicLimitInformation: JOBOBJECT_BASIC_LIMIT_INFORMATION,
            IoInfo: IO_COUNTERS,
            ProcessMemoryLimit: 'size_t',
            JobMemoryLimit: 'size_t',
            PeakProcessMemoryUsed: 'size_t',
            PeakJobMemoryUsed: 'size_t'
        })

        const CreateJobObjectW = kernel32!.func('__stdcall', 'CreateJobObjectW', HANDLE, ['void *', 'str16'])
        const AssignProcessToJobObject = kernel32!.func('__stdcall', 'AssignProcessToJobObject', 'bool', [HANDLE, HANDLE])
        const SetInformationJobObject = kernel32!.func('__stdcall', 'SetInformationJobObject', 'bool', [HANDLE, 'int', 'void *', 'uint32'])
        const OpenProcess = kernel32!.func('__stdcall', 'OpenProcess', HANDLE, ['uint32', 'bool', 'uint32'])
        const CloseHandle = kernel32!.func('__stdcall', 'CloseHandle', 'bool', [HANDLE])

        jobApi = {
            createJobObject: () => CreateJobObjectW(null, null),
            setInformation: (hJob, buf) => SetInformationJobObject(hJob, JobObjectExtendedLimitInformation, buf, buf.length),
            openProcess: (pid) => OpenProcess(PROCESS_ALL_ACCESS, false, pid),
            assign: (hJob, hProc) => AssignProcessToJobObject(hJob, hProc),
            closeHandle: (h) => CloseHandle(h),
            extLimitInfo: JOBOBJECT_EXTENDED_LIMIT_INFORMATION,
            sizeofExtLimitInfo: koffi!.sizeof(JOBOBJECT_EXTENDED_LIMIT_INFORMATION),
            encode: (buf, type, obj) => koffi!.encode(buf, type as never, obj),
            isNull: (h) => h == null
        }
        return jobApi
    } catch {
        // koffi 不可用：静默降级
        jobApi = null
        return null
    }
}

/**
 * 创建一个配置好资源限制与 KILL_ON_JOB_CLOSE 的 Job Object。
 * 非 Windows 或 koffi 不可用时返回 null，调用方应回退到进程组方案。
 */
export function createJob(limits: JobLimits): JobHandle | null {
    const api = ensureLoaded()
    if (!api) return null

    const hJob = api.createJobObject()
    if (api.isNull(hJob)) return null

    let limitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE
    const memMB = limits.memMB && limits.memMB > 0 ? limits.memMB : 0
    const maxProcesses = limits.maxProcesses && limits.maxProcesses > 0 ? limits.maxProcesses : 0
    if (memMB > 0) limitFlags |= JOB_OBJECT_LIMIT_PROCESS_MEMORY
    if (maxProcesses > 0) limitFlags |= JOB_OBJECT_LIMIT_ACTIVE_PROCESS

    const info = {
        BasicLimitInformation: {
            PerProcessUserTimeLimit: 0,
            PerJobUserTimeLimit: 0,
            LimitFlags: limitFlags,
            MinimumWorkingSetSize: 0,
            MaximumWorkingSetSize: 0,
            ActiveProcessLimit: maxProcesses,
            Affinity: 0,
            PriorityClass: 0,
            SchedulingClass: 0
        },
        IoInfo: {
            ReadOperationCount: 0, WriteOperationCount: 0, OtherOperationCount: 0,
            ReadTransferCount: 0, WriteTransferCount: 0, OtherTransferCount: 0
        },
        ProcessMemoryLimit: memMB > 0 ? memMB * 1024 * 1024 : 0,
        JobMemoryLimit: 0,
        PeakProcessMemoryUsed: 0,
        PeakJobMemoryUsed: 0
    }

    const buf = Buffer.alloc(api.sizeofExtLimitInfo)
    api.encode(buf, api.extLimitInfo, info)
    if (!api.setInformation(hJob, buf)) {
        api.closeHandle(hJob)
        return null
    }

    let closed = false
    return {
        assignPid: (pid: number): boolean => {
            const hProc = api.openProcess(pid)
            if (api.isNull(hProc)) return false
            try {
                return api.assign(hJob, hProc)
            } finally {
                api.closeHandle(hProc)
            }
        },
        close: (): void => {
            if (closed) return
            closed = true
            api.closeHandle(hJob)
        }
    }
}
