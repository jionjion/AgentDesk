import {app, BrowserWindow, ipcMain, Menu, nativeImage, session, shell, Tray} from 'electron'
import {join} from 'path'
import {existsSync, mkdirSync, readFileSync, writeFileSync} from 'fs'
import {electronApp, is, optimizer} from '@electron-toolkit/utils'
import {registerIpcHandlers} from './ipc'

type CloseAction = 'ask' | 'minimize' | 'quit'

// ── 本地设置持久化 ──────────────────────────────
const settingsDir = join(app.getPath('userData'), 'local-settings')
const settingsFile = join(settingsDir, 'settings.json')

function readLocalSettings(): Record<string, unknown> {
    try {
        if (existsSync(settingsFile)) {
            return JSON.parse(readFileSync(settingsFile, 'utf-8'))
        }
    } catch {
        // ignore
    }
    return {}
}

function writeLocalSettings(data: Record<string, unknown>): void {
    if (!existsSync(settingsDir)) {
        mkdirSync(settingsDir, {recursive: true})
    }
    writeFileSync(settingsFile, JSON.stringify(data, null, 2), 'utf-8')
}

function getCloseAction(): CloseAction {
    const s = readLocalSettings()
    return (s.closeAction as CloseAction) || 'ask'
}

function setCloseAction(action: CloseAction): void {
    const s = readLocalSettings()
    s.closeAction = action
    writeLocalSettings(s)
}

// ── 应用图标 ─────────────────────────────────────
function getAppIcon(): Electron.NativeImage {
    const iconPath = is.dev
        ? join(app.getAppPath(), 'resources/icon_255.png')
        : join(process.resourcesPath, 'icon_255.png')
    return nativeImage.createFromPath(iconPath)
}

/** 对 NativeImage 做圆角裁剪（带抗锯齿） */
function roundIcon(image: Electron.NativeImage, radius: number): Electron.NativeImage {
    const {width, height} = image.getSize()
    const bitmap = image.toBitmap()
    const r = Math.min(radius, width / 2, height / 2)

    // 四个圆心坐标
    const corners = [
        {cx: r, cy: r},                         // 左上
        {cx: width - r - 1, cy: r},              // 右上
        {cx: r, cy: height - r - 1},             // 左下
        {cx: width - r - 1, cy: height - r - 1}  // 右下
    ]

    for (let y = 0; y < height; y++) {
        for (let x = 0; x < width; x++) {
            let cornerDist = -1
            // 判断是否落在某个圆角区域内
            if (x < r && y < r) {
                cornerDist = Math.sqrt((x - corners[0].cx) ** 2 + (y - corners[0].cy) ** 2)
            } else if (x > width - r - 1 && y < r) {
                cornerDist = Math.sqrt((x - corners[1].cx) ** 2 + (y - corners[1].cy) ** 2)
            } else if (x < r && y > height - r - 1) {
                cornerDist = Math.sqrt((x - corners[2].cx) ** 2 + (y - corners[2].cy) ** 2)
            } else if (x > width - r - 1 && y > height - r - 1) {
                cornerDist = Math.sqrt((x - corners[3].cx) ** 2 + (y - corners[3].cy) ** 2)
            }

            if (cornerDist < 0) continue // 不在圆角区域，保持原样

            const idx = (y * width + x) * 4
            if (cornerDist > r + 0.5) {
                // 完全在圆角外
                bitmap[idx + 3] = 0
            } else if (cornerDist > r - 0.5) {
                // 边缘 1px 过渡带：按距离线性插值 alpha，实现抗锯齿
                const alpha = Math.round((r + 0.5 - cornerDist) * bitmap[idx + 3])
                bitmap[idx + 3] = alpha
            }
        }
    }

    return nativeImage.createFromBuffer(bitmap, {width, height})
}

let mainWindow: BrowserWindow | null = null
let tray: Tray | null = null
let forceQuit = false

function createWindow(): void {
    mainWindow = new BrowserWindow({
        width: 1280,
        height: 860,
        minWidth: 900,
        minHeight: 600,
        show: false,
        frame: false,
        titleBarStyle: 'hidden',
        icon: roundIcon(getAppIcon(), 50),
        ...(process.platform === 'win32' ? {thickFrame: true} : {}),
        webPreferences: {
            preload: join(__dirname, '../preload/index.js'),
            contextIsolation: true,
            nodeIntegration: false,
            sandbox: false
        }
    })

    mainWindow.on('ready-to-show', () => {
        mainWindow!.show()
    })

    mainWindow.webContents.setWindowOpenHandler((details) => {
        shell.openExternal(details.url)
        return {action: 'deny'}
    })

    // ── 关闭拦截 ──────────────────────────────────
    mainWindow.on('close', (event) => {
        if (forceQuit) return // 允许真正退出

        const action = getCloseAction()

        if (action === 'quit') {
            return // 正常关闭
        }

        if (action === 'minimize') {
            event.preventDefault()
            mainWindow?.hide()
            return
        }

        // action === 'ask' → 通知渲染进程弹出对话框
        event.preventDefault()
        mainWindow?.webContents.send('show-close-dialog')
    })

    // 窗口控制 IPC
    ipcMain.on('window:minimize', () => mainWindow?.minimize())
    ipcMain.on('window:maximize', () => {
        if (mainWindow?.isMaximized()) {
            mainWindow.unmaximize()
        } else {
            mainWindow?.maximize()
        }
    })
    ipcMain.on('window:close', () => mainWindow?.close())

    // ── 关闭行为 IPC ──────────────────────────────
    ipcMain.handle('app:getCloseAction', () => getCloseAction())

    ipcMain.handle('app:setCloseAction', (_event, action: CloseAction) => {
        setCloseAction(action)
    })

    ipcMain.on('app:confirmClose', (_event, choice: 'quit' | 'minimize') => {
        if (choice === 'minimize') {
            mainWindow?.hide()
        } else {
            forceQuit = true
            mainWindow?.close()
        }
    })

    // 开发模式加载 dev server，生产模式加载本地文件
    if (is.dev && process.env['ELECTRON_RENDERER_URL']) {
        mainWindow.loadURL(process.env['ELECTRON_RENDERER_URL'])
    } else {
        mainWindow.loadFile(join(__dirname, '../renderer/index.html'))
    }
}

function createTray(): void {
    const icon = getAppIcon().resize({width: 16, height: 16})
    tray = new Tray(roundIcon(icon, 3))
    tray.setToolTip('AgentDesk')

    const contextMenu = Menu.buildFromTemplate([
        {
            label: '显示窗口',
            click: () => {
                mainWindow?.show()
                mainWindow?.focus()
            }
        },
        {type: 'separator'},
        {
            label: '退出',
            click: () => {
                forceQuit = true
                app.quit()
            }
        }
    ])

    tray.setContextMenu(contextMenu)

    tray.on('double-click', () => {
        mainWindow?.show()
        mainWindow?.focus()
    })
}

app.whenReady().then(() => {
    electronApp.setAppUserModelId('top.jionjion.agentdesk')

    // 允许加载外部图片（如 OSS 头像）
    const apiBaseUrl: string = __API_BASE_URL__

    // 生产模式下 file:// 协议的 Origin 为 null，后端 CORS 会拒绝，需替换为合法 Origin
    session.defaultSession.webRequest.onBeforeSendHeaders((details, callback) => {
        const origin = details.requestHeaders['Origin']
        if (!origin || origin === 'null' || origin.startsWith('file://')) {
            details.requestHeaders['Origin'] = apiBaseUrl
        }
        callback({requestHeaders: details.requestHeaders})
    })

    session.defaultSession.webRequest.onHeadersReceived((details, callback) => {
        callback({
            responseHeaders: {
                ...details.responseHeaders,
                'Content-Security-Policy': [
                    `default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob: https: http:; connect-src 'self' ${apiBaseUrl} https:; font-src 'self' data:`
                ]
            }
        })
    })

    app.on('browser-window-created', (_, window) => {
        optimizer.watchWindowShortcuts(window)
    })

    registerIpcHandlers()
    createWindow()
    createTray()

    app.on('activate', () => {
        if (BrowserWindow.getAllWindows().length === 0) {
            createWindow()
        }
    })
})

app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') {
        app.quit()
    }
})
