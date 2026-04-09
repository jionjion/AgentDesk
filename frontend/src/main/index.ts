import { app, shell, BrowserWindow, ipcMain, session, Tray, Menu, nativeImage } from 'electron'
import { join } from 'path'
import { readFileSync, writeFileSync, existsSync, mkdirSync } from 'fs'
import { electronApp, optimizer, is } from '@electron-toolkit/utils'
import { registerIpcHandlers } from './ipc'

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
    mkdirSync(settingsDir, { recursive: true })
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

// ── 托盘图标 ─────────────────────────────────────
function createTrayIcon(): Electron.NativeImage {
  // 创建一个 16x16 的简单图标（蓝色圆形）
  const size = 16
  const canvas = Buffer.alloc(size * size * 4)
  const cx = size / 2, cy = size / 2, r = 6
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const idx = (y * size + x) * 4
      const dist = Math.sqrt((x - cx) ** 2 + (y - cy) ** 2)
      if (dist <= r) {
        canvas[idx] = 59     // R
        canvas[idx + 1] = 130 // G
        canvas[idx + 2] = 246 // B
        canvas[idx + 3] = 255 // A
      }
    }
  }
  return nativeImage.createFromBuffer(canvas, { width: size, height: size })
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
    ...(process.platform === 'win32' ? { thickFrame: true } : {}),
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
    return { action: 'deny' }
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
  const icon = createTrayIcon()
  tray = new Tray(icon)
  tray.setToolTip('AgentDesk')

  const contextMenu = Menu.buildFromTemplate([
    {
      label: '显示窗口',
      click: () => {
        mainWindow?.show()
        mainWindow?.focus()
      }
    },
    { type: 'separator' },
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
  session.defaultSession.webRequest.onHeadersReceived((details, callback) => {
    callback({
      responseHeaders: {
        ...details.responseHeaders,
        'Content-Security-Policy': [
          "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob: https: http:; connect-src 'self' http://localhost:8080 https:; font-src 'self' data:"
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
