import { contextBridge, ipcRenderer } from 'electron'

const electronAPI = {
  dialog: {
    openFile: (): Promise<string[]> => ipcRenderer.invoke('dialog:openFile'),
    saveFile: (options?: { defaultPath?: string; filters?: { name: string; extensions: string[] }[] }): Promise<string> =>
      ipcRenderer.invoke('dialog:saveFile', options)
  },
  fs: {
    readFile: (filePath: string): Promise<Uint8Array> =>
      ipcRenderer.invoke('fs:readFile', filePath),
    writeFile: (filePath: string, data: Uint8Array): Promise<void> =>
      ipcRenderer.invoke('fs:writeFile', filePath, data),
    readDirectory: (dirPath: string): Promise<string[]> =>
      ipcRenderer.invoke('fs:readDirectory', dirPath)
  },
  shell: {
    openExternal: (url: string): Promise<void> =>
      ipcRenderer.invoke('shell:openExternal', url)
  },
  app: {
    getVersion: (): Promise<string> => ipcRenderer.invoke('app:getVersion')
  },
  window: {
    minimize: (): void => ipcRenderer.send('window:minimize'),
    maximize: (): void => ipcRenderer.send('window:maximize'),
    close: (): void => ipcRenderer.send('window:close')
  }
}

contextBridge.exposeInMainWorld('electronAPI', electronAPI)

export type ElectronAPI = typeof electronAPI
