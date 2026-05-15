import {app, dialog, ipcMain, shell} from 'electron'
import {readdir, readFile, writeFile} from 'fs/promises'
import {spawn} from 'child_process'
import os from 'os'

export function registerIpcHandlers(): void {
    // 文件对话框
    ipcMain.handle('dialog:openFile', async () => {
        const result = await dialog.showOpenDialog({
            properties: ['openFile', 'multiSelections']
        })
        return result.canceled ? [] : result.filePaths
    })

    ipcMain.handle('dialog:saveFile', async (_event, options?: { defaultPath?: string; filters?: { name: string; extensions: string[] }[] }) => {
        const result = await dialog.showSaveDialog({
            defaultPath: options?.defaultPath,
            filters: options?.filters
        })
        return result.canceled ? '' : result.filePath
    })

    ipcMain.handle('dialog:openDirectory', async () => {
        const result = await dialog.showOpenDialog({properties: ['openDirectory']})
        return result.canceled ? '' : result.filePaths[0]
    })

    // 文件系统操作
    ipcMain.handle('fs:readFile', async (_event, filePath: string) => {
        const data = await readFile(filePath)
        return new Uint8Array(data)
    })

    ipcMain.handle('fs:writeFile', async (_event, filePath: string, data: Uint8Array) => {
        await writeFile(filePath, data)
    })

    ipcMain.handle('fs:readDirectory', async (_event, dirPath: string) => {
        const entries = await readdir(dirPath)
        return entries
    })

    // Shell 操作
    ipcMain.handle('shell:openExternal', async (_event, url: string) => {
        await shell.openExternal(url)
    })

    // Shell 命令执行（远程执行功能）
    ipcMain.handle('shell:execute', async (_event, command: string, workingDir?: string) => {
        return executeShellCommand(command, workingDir)
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
}

/**
 * 执行 shell 命令并返回结果
 */
function executeShellCommand(command: string, workingDir?: string): Promise<{ exitCode: number; stdout: string; stderr: string; durationMs: number }> {
    return new Promise((resolve) => {
        const startTime = Date.now()
        const isWindows = process.platform === 'win32'

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
            windowsHide: true
        })

        let stdout = ''
        let stderr = ''

        child.stdout.on('data', (data: Buffer) => {
            stdout += data.toString()
        })

        child.stderr.on('data', (data: Buffer) => {
            stderr += data.toString()
        })

        child.on('close', (code) => {
            resolve({
                exitCode: code ?? -1,
                stdout,
                stderr,
                durationMs: Date.now() - startTime
            })
        })

        child.on('error', (err) => {
            resolve({
                exitCode: -1,
                stdout: '',
                stderr: err.message,
                durationMs: Date.now() - startTime
            })
        })
    })
}

