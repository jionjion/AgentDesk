import {lstat, mkdir, readdir, readFile, rename, stat, writeFile} from 'fs/promises'
import path from 'path'

/**
 * 本地文件 RPC 实现 (见开发计划 8.3/9.4)。
 *
 * 操作: read / write / edit / list / glob / grep / stat。
 * 所有限制在客户端强制执行（后端交给模型前还有第二层截断）：
 * - read: 单次最多扫描 512KiB、64,000 字符、5,000 行、单行 20,000 字符
 * - write/edit: 目标 2MiB
 * - list/glob: 1,000 条目
 * - grep: 200 匹配、单条 4,000 字符
 * 文本仅接受 UTF-8/UTF-8 BOM；二进制返回错误与元数据。
 */

const MAX_READ_BYTES = 512 * 1024
const MAX_OUTPUT_CHARS = 64_000
const MAX_LINES = 5_000
const MAX_LINE_CHARS = 20_000
const MAX_WRITE_BYTES = 2 * 1024 * 1024
const MAX_ENTRIES = 1_000
const MAX_MATCHES = 200
const MAX_MATCH_CHARS = 4_000
const MAX_GREP_SCAN_FILES = 5_000
const MAX_GLOB_DEPTH = 12

/** 默认排除的目录（glob/grep 递归时跳过） */
const EXCLUDED_DIRS = new Set([
    'node_modules', '.git', '.svn', 'dist', 'out', 'build', 'target',
    '.venv', 'venv', '__pycache__', '.idea', '.vscode', 'coverage'
])

export interface LocalFsRequest {
    op: 'read' | 'write' | 'edit' | 'list' | 'glob' | 'grep' | 'stat'
    path: string
    // read
    offset?: number
    limit?: number
    // write
    content?: string
    // edit
    oldText?: string
    newText?: string
    replaceAll?: boolean
    expectedReplacements?: number
    // glob/grep
    pattern?: string
    query?: string
    filePattern?: string
}

export type LocalFsResult = Record<string, unknown>

/** 统一入口：按 op 分发。所有错误转为 { success: false, error } */
export async function handleLocalFs(request: LocalFsRequest): Promise<LocalFsResult> {
    try {
        switch (request.op) {
            case 'read':
                return await opRead(request)
            case 'write':
                return await opWrite(request)
            case 'edit':
                return await opEdit(request)
            case 'list':
                return await opList(request)
            case 'glob':
                return await opGlob(request)
            case 'grep':
                return await opGrep(request)
            case 'stat':
                return await opStat(request)
            default:
                return {success: false, error: `不支持的操作: ${request.op}`}
        }
    } catch (e) {
        return {success: false, error: e instanceof Error ? e.message : String(e)}
    }
}

// ==================== 编码与换行 ====================

/** 判断 Buffer 是否疑似二进制（含 NUL 或 UTF-8 解码替换率过高） */
function looksBinary(buf: Buffer): boolean {
    const sample = buf.subarray(0, Math.min(buf.length, 8192))
    if (sample.includes(0)) return true
    const text = sample.toString('utf8')
    let bad = 0
    for (const ch of text) {
        if (ch === '\uFFFD') bad++
    }
    return text.length > 0 && bad / text.length > 0.05
}

interface DecodedText {
    text: string
    hasBom: boolean
}

function decodeUtf8(buf: Buffer): DecodedText {
    const hasBom = buf.length >= 3 && buf[0] === 0xef && buf[1] === 0xbb && buf[2] === 0xbf
    const body = hasBom ? buf.subarray(3) : buf
    return {text: body.toString('utf8'), hasBom}
}

type LineEnding = 'crlf' | 'lf' | 'mixed' | 'none'

function detectLineEnding(text: string): LineEnding {
    const crlf = (text.match(/\r\n/g) || []).length
    const lfOnly = (text.match(/(?<!\r)\n/g) || []).length
    if (crlf > 0 && lfOnly > 0) return 'mixed'
    if (crlf > 0) return 'crlf'
    if (lfOnly > 0) return 'lf'
    return 'none'
}

/** 同目录临时文件 + 原子替换写入 */
async function atomicWrite(target: string, data: Buffer): Promise<void> {
    await mkdir(path.dirname(target), {recursive: true})
    const tmp = `${target}.${process.pid}.${Date.now()}.tmp`
    await writeFile(tmp, data)
    await rename(tmp, target)
}

// ==================== read ====================

async function opRead(request: LocalFsRequest): Promise<LocalFsResult> {
    const filePath = request.path
    const s = await stat(filePath)
    if (!s.isFile()) {
        return {success: false, error: '目标不是文件'}
    }
    if (looksBinaryByExt(filePath)) {
        return {success: false, error: `疑似二进制文件 (${path.extname(filePath)}), 不返回内容`, size: s.size}
    }

    // 只读取有限字节量（大文件不整读）
    const buf = await readFile(filePath)
    if (looksBinary(buf)) {
        return {success: false, error: '二进制文件或非 UTF-8 编码, 不返回内容', size: s.size}
    }
    const {text} = decodeUtf8(buf.subarray(0, Math.min(buf.length, MAX_READ_BYTES * 4)))
    const allLines = text.split('\n')
    const totalLines = allLines.length

    const startLine = request.offset && request.offset > 0 ? request.offset : 1
    const requestedLimit = request.limit && request.limit > 0 ? Math.min(request.limit, MAX_LINES) : MAX_LINES

    let charBudget = MAX_OUTPUT_CHARS
    let byteBudget = MAX_READ_BYTES
    const outLines: string[] = []
    let line = startLine
    let truncated = false
    for (; line <= totalLines && outLines.length < requestedLimit; line++) {
        let content = allLines[line - 1]
        // 移除行尾 \r（读出内容统一 LF 语义）
        if (content.endsWith('\r')) content = content.slice(0, -1)
        if (content.length > MAX_LINE_CHARS) {
            content = content.slice(0, MAX_LINE_CHARS) + '…[行截断]'
        }
        const cost = content.length + 1
        if (cost > charBudget || Buffer.byteLength(content, 'utf8') + 1 > byteBudget) {
            truncated = true
            break
        }
        charBudget -= cost
        byteBudget -= Buffer.byteLength(content, 'utf8') + 1
        outLines.push(content)
    }
    if (line <= totalLines && outLines.length >= requestedLimit) {
        truncated = true
    }

    return {
        success: true,
        path: filePath,
        content: outLines.join('\n'),
        totalLines,
        size: s.size,
        truncated,
        ...(truncated ? {nextOffset: startLine + outLines.length} : {})
    }
}

const BINARY_EXTS = new Set([
    '.png', '.jpg', '.jpeg', '.gif', '.webp', '.ico', '.bmp', '.pdf', '.zip', '.gz',
    '.tar', '.7z', '.rar', '.exe', '.dll', '.so', '.dylib', '.class', '.jar', '.war',
    '.woff', '.woff2', '.ttf', '.eot', '.mp3', '.mp4', '.avi', '.mov', '.db', '.sqlite'
])

function looksBinaryByExt(filePath: string): boolean {
    return BINARY_EXTS.has(path.extname(filePath).toLowerCase())
}

// ==================== write ====================

async function opWrite(request: LocalFsRequest): Promise<LocalFsResult> {
    const content = request.content ?? ''
    const data = Buffer.from(content, 'utf8')
    if (data.length > MAX_WRITE_BYTES) {
        return {
            success: false,
            error: `内容超过写入上限 (${Math.round(MAX_WRITE_BYTES / 1024)}KiB), 请分块写入或改用脚本`
        }
    }
    await atomicWrite(request.path, data)
    return {success: true, path: request.path, bytesWritten: data.length}
}

// ==================== edit ====================

async function opEdit(request: LocalFsRequest): Promise<LocalFsResult> {
    const filePath = request.path
    const oldText = request.oldText
    const newText = request.newText
    if (!oldText || newText === undefined) {
        return {success: false, error: 'oldText/newText 不能为空'}
    }
    const s = await stat(filePath)
    if (!s.isFile()) {
        return {success: false, error: '目标不是文件'}
    }
    if (s.size > MAX_WRITE_BYTES) {
        return {success: false, error: `文件超过编辑上限 (${Math.round(MAX_WRITE_BYTES / 1024)}KiB)`}
    }
    const buf = await readFile(filePath)
    if (looksBinary(buf)) {
        return {success: false, error: '二进制文件或非 UTF-8 编码, 无法编辑'}
    }
    const {text, hasBom} = decodeUtf8(buf)
    const ending = detectLineEnding(text)
    if (ending === 'mixed') {
        return {success: false, error: '文件含混合换行符 (CRLF 与 LF 混用), 拒绝自动规范化。请先统一换行符或用 shell 处理'}
    }

    // 统一规范化为 LF 后匹配, 写回时恢复原换行风格
    const normalizedContent = ending === 'crlf' ? text.replace(/\r\n/g, '\n') : text
    const normalizedOld = oldText.replace(/\r\n/g, '\n')
    const normalizedNew = newText.replace(/\r\n/g, '\n')

    const occurrences = countOccurrences(normalizedContent, normalizedOld)
    const replaceAll = request.replaceAll === true
    const expected = request.expectedReplacements ?? 1

    if (occurrences === 0) {
        return {success: false, error: 'old_text 在文件中未找到, 请确认内容与缩进精确匹配'}
    }
    if (!replaceAll && occurrences !== 1) {
        return {success: false, error: `old_text 在文件中出现 ${occurrences} 次 (要求恰好 1 次)。请提供更长的唯一上下文, 或使用 replace_all`}
    }
    const actual = replaceAll ? occurrences : 1
    if (actual !== expected) {
        return {success: false, error: `实际替换次数 ${actual} 与 expected_replacements=${expected} 不一致, 未修改文件`}
    }

    const updated = replaceAll
        ? normalizedContent.split(normalizedOld).join(normalizedNew)
        : normalizedContent.replace(normalizedOld, () => normalizedNew)
    const restored = ending === 'crlf' ? updated.replace(/\n/g, '\r\n') : updated
    const outBuf = Buffer.concat([
        hasBom ? Buffer.from([0xef, 0xbb, 0xbf]) : Buffer.alloc(0),
        Buffer.from(restored, 'utf8')
    ])
    if (outBuf.length > MAX_WRITE_BYTES) {
        return {success: false, error: '编辑后内容超过写入上限, 未修改文件'}
    }
    await atomicWrite(filePath, outBuf)
    return {success: true, path: filePath, replacements: actual}
}

function countOccurrences(text: string, search: string): number {
    let count = 0
    let index = 0
    while ((index = text.indexOf(search, index)) !== -1) {
        count++
        index += search.length
    }
    return count
}

// ==================== list ====================

async function opList(request: LocalFsRequest): Promise<LocalFsResult> {
    const dirPath = request.path
    const entries = await readdir(dirPath, {withFileTypes: true})
    const out: string[] = []
    let truncated = false
    for (const entry of entries) {
        if (out.length >= MAX_ENTRIES) {
            truncated = true
            break
        }
        if (entry.isDirectory()) {
            out.push(entry.name + '/')
        } else {
            let size = 0
            try {
                size = (await stat(path.join(dirPath, entry.name))).size
            } catch { /* 忽略无法 stat 的项 */ }
            out.push(`${entry.name} (${formatSize(size)})`)
        }
    }
    // 目录在前, 各自按名称排序
    out.sort((a, b) => {
        const aDir = a.endsWith('/') ? 0 : 1
        const bDir = b.endsWith('/') ? 0 : 1
        return aDir - bDir || a.localeCompare(b)
    })
    return {success: true, path: dirPath, entries: out, truncated}
}

function formatSize(bytes: number): string {
    if (bytes < 1024) return `${bytes}B`
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)}KB`
    return `${(bytes / (1024 * 1024)).toFixed(1)}MB`
}

// ==================== glob ====================

/** 简易 glob 编译: 支持 **, *, ? */
function compileGlob(pattern: string): RegExp {
    const normalized = pattern.replace(/\\/g, '/')
    let regex = ''
    for (let i = 0; i < normalized.length; i++) {
        const ch = normalized[i]
        if (ch === '*') {
            if (normalized[i + 1] === '*') {
                // '**/' 或 '**'
                if (normalized[i + 2] === '/') {
                    regex += '(?:.*/)?'
                    i += 2
                } else {
                    regex += '.*'
                    i += 1
                }
            } else {
                regex += '[^/]*'
            }
        } else if (ch === '?') {
            regex += '[^/]'
        } else if ('\\^$.|+()[]{}'.includes(ch)) {
            regex += '\\' + ch
        } else {
            regex += ch
        }
    }
    return new RegExp(`^${regex}$`, process.platform === 'win32' ? 'i' : '')
}

async function collectFiles(
    rootDir: string,
    matcher: (relativePath: string) => boolean,
    maxCount: number
): Promise<{files: string[]; truncated: boolean; scanned: number}> {
    const files: string[] = []
    let truncated = false
    let scanned = 0

    async function walk(dir: string, relative: string, depth: number): Promise<void> {
        if (truncated || depth > MAX_GLOB_DEPTH) return
        let entries
        try {
            entries = await readdir(dir, {withFileTypes: true})
        } catch {
            return
        }
        for (const entry of entries) {
            if (truncated) return
            if (entry.name.startsWith('.') && entry.name !== '.') {
                if (EXCLUDED_DIRS.has(entry.name)) continue
            }
            const rel = relative ? `${relative}/${entry.name}` : entry.name
            if (entry.isDirectory()) {
                if (EXCLUDED_DIRS.has(entry.name)) continue
                await walk(path.join(dir, entry.name), rel, depth + 1)
            } else if (entry.isFile()) {
                scanned++
                if (scanned > MAX_GREP_SCAN_FILES) {
                    truncated = true
                    return
                }
                if (matcher(rel)) {
                    if (files.length >= maxCount) {
                        truncated = true
                        return
                    }
                    files.push(rel)
                }
            }
        }
    }

    await walk(rootDir, '', 0)
    return {files, truncated, scanned}
}

async function opGlob(request: LocalFsRequest): Promise<LocalFsResult> {
    const pattern = request.pattern
    if (!pattern) {
        return {success: false, error: 'pattern 不能为空'}
    }
    const matcher = compileGlob(pattern)
    const {files, truncated} = await collectFiles(
        request.path, rel => matcher.test(rel), MAX_ENTRIES)
    files.sort()
    return {success: true, path: request.path, entries: files, truncated}
}

// ==================== grep ====================

async function opGrep(request: LocalFsRequest): Promise<LocalFsResult> {
    const query = request.query
    if (!query) {
        return {success: false, error: 'query 不能为空'}
    }
    let regex: RegExp
    try {
        regex = new RegExp(query)
    } catch {
        // 非法正则按字面文本搜索
        regex = new RegExp(query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'))
    }
    const fileMatcher = request.filePattern ? compileGlob(request.filePattern) : null

    const {files, scanned} = await collectFiles(
        request.path,
        rel => {
            if (looksBinaryByExt(rel)) return false
            if (fileMatcher) {
                return fileMatcher.test(rel) || fileMatcher.test(path.basename(rel))
            }
            return true
        },
        MAX_GREP_SCAN_FILES)

    const matches: string[] = []
    let truncated = false
    let charBudget = MAX_OUTPUT_CHARS
    for (const rel of files) {
        if (truncated) break
        let buf: Buffer
        try {
            buf = await readFile(path.join(request.path, rel))
        } catch {
            continue
        }
        if (buf.length > MAX_READ_BYTES * 2 || looksBinary(buf)) continue
        const {text} = decodeUtf8(buf)
        const lines = text.split(/\r?\n/)
        for (let i = 0; i < lines.length; i++) {
            if (regex.test(lines[i])) {
                let lineText = lines[i]
                if (lineText.length > MAX_MATCH_CHARS) {
                    lineText = lineText.slice(0, MAX_MATCH_CHARS) + '…[截断]'
                }
                const entry = `${rel}:${i + 1}: ${lineText}`
                if (matches.length >= MAX_MATCHES || entry.length + 1 > charBudget) {
                    truncated = true
                    break
                }
                charBudget -= entry.length + 1
                matches.push(entry)
            }
        }
    }
    return {
        success: true,
        path: request.path,
        matches,
        scannedFiles: files.length,
        totalScanned: scanned,
        truncated
    }
}

// ==================== stat ====================

async function opStat(request: LocalFsRequest): Promise<LocalFsResult> {
    const s = await lstat(request.path)
    return {
        success: true,
        path: request.path,
        type: s.isDirectory() ? 'directory' : s.isSymbolicLink() ? 'symlink' : 'file',
        size: s.size,
        modifiedAt: s.mtimeMs
    }
}
