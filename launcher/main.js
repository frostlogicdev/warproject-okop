const { app, BrowserWindow, ipcMain, shell } = require('electron');
const path = require('path');
const { spawn } = require('child_process');
const fs = require('fs');

let mainWindow;

const isDev = !app.isPackaged;

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 720,
    minWidth: 1024,
    minHeight: 600,
    frame: false,
    transparent: false,
    backgroundColor: '#0a0a0f',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false
    },
    icon: path.join(__dirname, 'assets', 'icon.png'),
    show: false
  });

  if (isDev) {
    mainWindow.loadURL('http://localhost:3000');
    mainWindow.webContents.openDevTools({ mode: 'detach' });
  } else {
    mainWindow.loadFile(path.join(__dirname, 'dist', 'index.html'));
  }

  mainWindow.once('ready-to-show', () => {
    mainWindow.show();
  });

  mainWindow.on('closed', () => {
    mainWindow = null;
  });
}

app.whenReady().then(createWindow);

app.on('window-all-closed', () => {
  app.quit();
});

// ─── IPC Handlers ─────────────────────────────────────────────────────────────

ipcMain.handle('window:minimize', () => mainWindow?.minimize());
ipcMain.handle('window:maximize', () => {
  if (mainWindow?.isMaximized()) {
    mainWindow.unmaximize();
  } else {
    mainWindow?.maximize();
  }
});
ipcMain.handle('window:close', () => mainWindow?.close());

ipcMain.handle('app:getVersion', () => app.getVersion());

ipcMain.handle('shell:openExternal', (_, url) => shell.openExternal(url));

// ─── Settings persistence ─────────────────────────────────────────────────────

const settingsPath = path.join(app.getPath('userData'), 'settings.json');

function loadSettings() {
  try {
    if (fs.existsSync(settingsPath)) {
      return JSON.parse(fs.readFileSync(settingsPath, 'utf-8'));
    }
  } catch (e) { /* ignore */ }
  return {
    ram: 4,
    javaPath: 'java',
    gameDir: path.join(app.getPath('appData'), '.warproject'),
    fullscreen: false,
    width: 1920,
    height: 1080
  };
}

function saveSettings(settings) {
  fs.writeFileSync(settingsPath, JSON.stringify(settings, null, 2));
}

ipcMain.handle('settings:load', () => loadSettings());
ipcMain.handle('settings:save', (_, settings) => {
  saveSettings(settings);
  return true;
});

// ─── Game Launch ──────────────────────────────────────────────────────────────

ipcMain.handle('game:launch', async (_, { token, username }) => {
  const settings = loadSettings();
  const gameDir = settings.gameDir;

  // Ensure game directory exists
  if (!fs.existsSync(gameDir)) {
    fs.mkdirSync(gameDir, { recursive: true });
  }

  // In production, this would download/verify game files and launch Minecraft
  // with the correct arguments. For now, we simulate the launch process.
  mainWindow?.webContents.send('game:status', { status: 'preparing', progress: 0 });

  // Simulate download/verification steps
  const steps = [
    { status: 'checking', progress: 10, label: 'Проверка файлов...' },
    { status: 'downloading', progress: 30, label: 'Загрузка обновлений...' },
    { status: 'verifying', progress: 60, label: 'Верификация модов...' },
    { status: 'launching', progress: 90, label: 'Запуск игры...' },
    { status: 'running', progress: 100, label: 'Игра запущена' }
  ];

  for (const step of steps) {
    await new Promise(resolve => setTimeout(resolve, 800));
    mainWindow?.webContents.send('game:status', step);
  }

  return { success: true };
});
