const { app, BrowserWindow, shell } = require('electron');
const { spawn } = require('child_process');
const path = require('path');
const http = require('http');
const fs = require('fs');

// Global error traps to prevent uncaught exception dialogs in the main process
process.on('uncaughtException', (err) => {
  console.error('[Zenith Desktop UncaughtException]', err);
});
process.on('unhandledRejection', (reason) => {
  console.error('[Zenith Desktop UnhandledRejection]', reason);
});

// Single instance enforcement: focus existing window if user opens a second instance
const gotTheLock = app.requestSingleInstanceLock();
if (!gotTheLock) {
  app.quit();
}

let mainWindow = null;
let serverProcess = null;
let pollTimeoutId = null;
let isUrlLoaded = false;
const PORT = 3000;
const URL = `http://localhost:${PORT}`;

// Check if Zenith Studio server is already running with idempotent single-call guard
function checkServer(callback) {
  let finished = false;
  const done = (status) => {
    if (finished) return;
    finished = true;
    callback(status);
  };

  try {
    const req = http.get(URL, (res) => {
      res.resume(); // consume response body to prevent socket leaks
      done(res.statusCode >= 200 && res.statusCode < 400);
    });

    req.on('error', () => done(false));
    req.setTimeout(800, () => {
      try { req.destroy(); } catch (_) {}
      done(false);
    });
  } catch (_) {
    done(false);
  }
}

function getJarPath() {
  // Packaged extraResources path
  if (process.resourcesPath) {
    const prodPath = path.join(process.resourcesPath, 'bin', 'zenith.jar');
    if (fs.existsSync(prodPath)) return prodPath;
  }

  // Development path
  const devPath = path.resolve(__dirname, '../bin/zenith.jar');
  if (fs.existsSync(devPath)) return devPath;

  return devPath;
}

function startZenithServer() {
  const jarPath = getJarPath();
  console.log('[Zenith Desktop] Embedded server jar:', jarPath);

  // Use a reliable writable working directory
  let workDir = path.resolve(__dirname, '..');
  if (app.isPackaged || !fs.existsSync(path.join(workDir, 'src'))) {
    workDir = process.env.USERPROFILE || app.getPath('documents') || process.cwd();
  }

  const args = ['-jar', jarPath, 'studio'];
  const userArgs = process.argv.slice(app.isPackaged ? 1 : 2).filter(a => !a.startsWith('--'));
  if (userArgs.length > 0 && fs.existsSync(userArgs[0])) {
    args.push(userArgs[0]);
  }

  try {
    serverProcess = spawn('java', args, {
      cwd: workDir,
      stdio: 'ignore',
      detached: false
    });

    serverProcess.on('error', (err) => {
      console.error('[Zenith Desktop] Failed to start Java process:', err);
    });
  } catch (err) {
    console.error('[Zenith Desktop] Spawn error:', err);
  }
}

function stopZenithServer() {
  if (pollTimeoutId) {
    clearTimeout(pollTimeoutId);
    pollTimeoutId = null;
  }
  if (serverProcess && serverProcess.pid) {
    try {
      if (process.platform === 'win32') {
        spawn('taskkill', ['/pid', String(serverProcess.pid), '/f', '/t'], { stdio: 'ignore' });
      } else {
        serverProcess.kill('SIGTERM');
      }
    } catch (_) {}
    serverProcess = null;
  }
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

  // Identify as Zenith native desktop app
  mainWindow.webContents.setUserAgent(
    mainWindow.webContents.getUserAgent() + ' ZenithStudioDesktop/1.0'
  );

  isUrlLoaded = false;

  function pollAndLoad() {
    if (isUrlLoaded || !mainWindow || mainWindow.isDestroyed()) return;

    checkServer((isUp) => {
      if (isUrlLoaded || !mainWindow || mainWindow.isDestroyed()) return;

      if (isUp) {
        isUrlLoaded = true;
        const targetUrl = `${URL}?mode=desktop`;
        mainWindow.loadURL(targetUrl).catch((err) => {
          console.error('[Zenith Desktop] loadURL error:', err);
        });
      } else {
        pollTimeoutId = setTimeout(pollAndLoad, 300);
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
    stopZenithServer();
  });
}

if (gotTheLock) {
  app.on('second-instance', () => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      if (mainWindow.isMinimized()) mainWindow.restore();
      mainWindow.focus();
    }
  });

  app.whenReady().then(() => {
    checkServer((alreadyRunning) => {
      if (!alreadyRunning) {
        startZenithServer();
      }
      createWindow();
    });
  });

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow();
    }
  });

  app.on('window-all-closed', () => {
    stopZenithServer();
    if (process.platform !== 'darwin') {
      app.quit();
    }
  });

  app.on('will-quit', () => {
    stopZenithServer();
  });
}
