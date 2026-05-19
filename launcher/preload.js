const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('electronAPI', {
  // Window controls
  minimize: () => ipcRenderer.invoke('window:minimize'),
  maximize: () => ipcRenderer.invoke('window:maximize'),
  close: () => ipcRenderer.invoke('window:close'),

  // App info
  getVersion: () => ipcRenderer.invoke('app:getVersion'),

  // External links
  openExternal: (url) => ipcRenderer.invoke('shell:openExternal', url),

  // Settings
  loadSettings: () => ipcRenderer.invoke('settings:load'),
  saveSettings: (settings) => ipcRenderer.invoke('settings:save', settings),

  // Game
  launchGame: (data) => ipcRenderer.invoke('game:launch', data),
  onGameStatus: (callback) => {
    ipcRenderer.on('game:status', (_, data) => callback(data));
    return () => ipcRenderer.removeAllListeners('game:status');
  }
});
