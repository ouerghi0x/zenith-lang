const { app, BrowserWindow, shell } = require('electron');
const { spawn } = require('child_process');
const path = require('path');
const http = require('http');

let mainWindow = null;
let serverProcess = null;
const PORT = 3000;
const URL = `http://localhost:${PORT}`;

// Check if Zenith Studio server is already running
function checkServer(callback) {
  const req = http.get(URL, (res) => {
    callback(true);
  });
  req.on('error', () => {
    callback(false);
  });
  req.setTimeout(600, () => {
    req.abort();
    callback(false);
  });
}

function getJarPath() {
  const fs = require('fs');
  // Development path
  const devPath = path.resolve(__dirname, '../bin/zenith.jar');
  if (fs.existsSync(devPath)) return devPath;

  // Packaged extraResources path
  if (process.resourcesPath) {
    const prodPath = path.join(process.resourcesPath, 'bin', 'zenith.jar');
    if (fs.existsSync(prodPath)) return prodPath;
  }

  return devPath;
}

function startZenithServer() {
  const jarPath = getJarPath();
  console.log('[Zenith Desktop] Spawning embedded server from:', jarPath);

  const workDir = path.resolve(__dirname, '..');
  serverProcess = spawn('java', ['-jar', jarPath, 'studio'], {
    cwd: workDir,
    stdio: 'ignore',
    detached: false
  });

  serverProcess.on('error', (err) => {
    console.error('[Zenith Desktop] Failed to start server:', err);
  });
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1440,
    height: 900,
    minWidth: 1024,
    minHeight: 700,
    title: 'Zenith Studio PRO',
    backgroundColor: '#090a0f',
    icon: path.join(__dirname, 'icon.png'),
    autoHideMenuBar: true,
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true
    }
  });

  function pollAndLoad() {
    checkServer((isUp) => {
      if (isUp) {
        mainWindow.loadURL(URL);
      } else {
        setTimeout(pollAndLoad, 300);
      }
    });
  }

  pollAndLoad();

  // Route external documentation and website links to standard system browser
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (!url.startsWith(URL)) {
      shell.openExternal(url);
      return { action: 'deny' };
    }
    return { action: 'allow' };
  });

  mainWindow.on('closed', () => {
    mainWindow = null;
    if (serverProcess) {
      serverProcess.kill();
      serverProcess = null;
    }
  });
}

app.whenReady().then(() => {
  checkServer((alreadyRunning) => {
    if (!alreadyRunning) {
      startZenithServer();
    }
    createWindow();
  });
});

app.on('window-all-closed', () => {
  if (serverProcess) {
    serverProcess.kill();
    serverProcess = null;
  }
  if (process.platform !== 'darwin') {
    app.quit();
  }
});

