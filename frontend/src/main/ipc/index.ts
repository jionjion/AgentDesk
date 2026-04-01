import { ipcMain, dialog, shell, app } from 'electron'
import { readFile, writeFile, readdir } from 'fs/promises'

export function registerIpcHandlers(): void {
  // 文件对话框
  ipcMain.handle('dialog:openFile', async () => {
    const result = await dialog.showOpenDialog({
      properties: ['openFile', 'multiSelections']
    })
    return result.canceled ? [] : result.filePaths
  })

  ipcMain.handle('dialog:saveFile', async () => {
    const result = await dialog.showSaveDialog({})
    return result.canceled ? '' : result.filePath
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

  // 应用信息
  ipcMain.handle('app:getVersion', () => {
    return app.getVersion()
  })
}
