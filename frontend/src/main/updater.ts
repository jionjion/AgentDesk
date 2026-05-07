import { autoUpdater } from 'electron-updater'
import { ipcMain, BrowserWindow, app } from 'electron'
import { join } from 'path'

export function initUpdater(mainWindow: BrowserWindow): void {
  autoUpdater.autoDownload = false
  autoUpdater.autoInstallOnAppQuit = true

  // 开发模式下强制使用 dev-app-update.yml 配置，否则会跳过更新检查
  if (!app.isPackaged) {
    autoUpdater.updateConfigPath = join(app.getAppPath(), 'dev-app-update.yml')
    autoUpdater.forceDevUpdateConfig = true
  }

  // 转发事件到渲染进程
  autoUpdater.on('checking-for-update', () => {
    mainWindow.webContents.send('updater:checking')
  })

  autoUpdater.on('update-available', (info) => {
    mainWindow.webContents.send('updater:available', {
      version: info.version,
      releaseDate: info.releaseDate,
      releaseNotes: info.releaseNotes || ''
    })
  })

  autoUpdater.on('update-not-available', () => {
    mainWindow.webContents.send('updater:not-available')
  })

  autoUpdater.on('download-progress', (progress) => {
    mainWindow.webContents.send('updater:progress', {
      percent: Math.round(progress.percent),
      transferred: progress.transferred,
      total: progress.total,
      bytesPerSecond: progress.bytesPerSecond
    })
  })

  autoUpdater.on('update-downloaded', () => {
    mainWindow.webContents.send('updater:downloaded')
  })

  autoUpdater.on('error', (err) => {
    mainWindow.webContents.send('updater:error', err.message)
  })

  // 渲染进程 → 主进程
  ipcMain.handle('updater:check', () => autoUpdater.checkForUpdates())
  ipcMain.handle('updater:download', () => autoUpdater.downloadUpdate())
  ipcMain.handle('updater:install', () => autoUpdater.quitAndInstall(false, true))
}
