/**
 * Inventory Manager - desktop shell.
 *
 * Responsibilities:
 *   1. Make sure the Spring Boot backend is running (spawn it if not),
 *      using the bundled JRE when present (portable layout) or system java.
 *   2. Wait for /api/health, then open the app window pointed at it.
 *   3. Shut the backend down cleanly when the window closes.
 */
const { app, BrowserWindow } = require('electron')
const { spawn } = require('child_process')
const http = require('http')
const path = require('path')
const fs = require('fs')

const PORT = 8475
const URL_BASE = `http://127.0.0.1:${PORT}`

// Portable layout root: the folder that contains backend/, runtime/, data/
// In dev this repo root is two levels up; when packaged it is app.getPath('exe') dir.
function detectRoot() {
  // packaged portable layout: <root>/app/main.js
  const appParent = path.resolve(__dirname, '..')
  if (fs.existsSync(path.join(appParent, 'backend', 'inventory-backend.jar'))) return appParent
  const exeDir = path.dirname(app.getPath('exe'))
  if (fs.existsSync(path.join(exeDir, 'backend', 'inventory-backend.jar'))) return exeDir
  // dev fallback: desktop/../
  return path.resolve(__dirname, '..')
}

let backendProc = null
let shuttingDown = false

let resolvedJavaPath = null

function javaExecutable(root) {
  if (resolvedJavaPath) return resolvedJavaPath
  const bundled = path.join(root, 'runtime', 'bin', 'java.exe')
  if (fs.existsSync(bundled)) { resolvedJavaPath = bundled; return bundled }
  // Resolve absolute path - spawning bare 'java' on Windows can end up going
  // through a shim/wrapper whose lifetime we cannot control.
  try {
    const out = require('child_process')
      .execSync('where.exe java.exe', { encoding: 'utf8' })
    const first = out.split(/\r?\n/).find((l) => l.trim().endsWith('.exe'))
    if (first) { resolvedJavaPath = first.trim(); return resolvedJavaPath }
  } catch { /* fall through */ }
  return 'java.exe'
}

function jarPath(root) {
  const candidates = [
    path.join(root, 'backend', 'inventory-backend.jar'),          // packaged layout
    path.join(root, 'backend', 'target', 'inventory-backend.jar') // dev layout
  ]
  const found = candidates.find((p) => fs.existsSync(p))
  if (!found) throw new Error(`Backend jar not found (tried: ${candidates.join(', ')})`)
  return found
}

function backendUp() {
  return new Promise((resolve) => {
    const req = http.get(`${URL_BASE}/api/health`, { timeout: 1500 }, (res) => {
      res.resume()
      resolve(res.statusCode === 200)
    })
    req.on('error', () => resolve(false))
    req.on('timeout', () => { req.destroy(); resolve(false) })
  })
}

async function waitForBackend(timeoutMs) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    if (await backendUp()) return true
    await new Promise((r) => setTimeout(r, 500))
  }
  return false
}

function startBackend(root) {
  const jar = jarPath(root)
  const cwd = path.dirname(jar)
  console.log('[shell] starting backend:', jar)
  backendProc = spawn(javaExecutable(root), ['-jar', jar], {
    cwd,
    windowsHide: true,
    stdio: ['ignore', 'pipe', 'pipe'],
  })
  const logStream = fs.createWriteStream(path.join(cwd, 'desktop-backend.log'), { flags: 'a' })
  backendProc.stdout.pipe(logStream)
  backendProc.stderr.pipe(logStream)
  backendProc.on('exit', (code) => console.log('[shell] backend exited:', code))
}

function stopBackend() {
  if (!backendProc || shuttingDown) return
  shuttingDown = true
  console.log('[shell] stopping backend pid', backendProc.pid)
  if (process.platform === 'win32') {
    const pid = backendProc.pid
    // Belt & braces: tree-kill the recorded pid, then also sweep any java.exe
    // whose command line references our jar (covers shimmed launches and orphans).
    const killer = spawn('taskkill', ['/pid', String(pid), '/T', '/F'],
      { windowsHide: true, detached: true, stdio: 'ignore' })
    killer.unref()
    try {
      require('child_process')
        .execSync("wmic process where \"name='java.exe' and commandline like '%inventory-backend.jar%'\" delete",
          { windowsHide: true, stdio: 'ignore' })
    } catch {
      try { require('child_process').execSync(
        'powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \\"Name=\'java.exe\'\\" | Where-Object { $_.CommandLine -match \'inventory-backend.jar\' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }"',
        { windowsHide: true, stdio: 'ignore' }) } catch { /* best effort */ }
    }
  } else {
    backendProc.kill()
  }
}

async function createWindow() {
  const root = detectRoot()

  let win = new BrowserWindow({
    width: 1366,
    height: 850,
    minWidth: 1024,
    minHeight: 700,
    title: 'Inventory Manager',
    backgroundColor: '#f4f6f8',
    autoHideMenuBar: true,
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  })

  win.on('closed', stopBackend)

  if (!(await backendUp())) {
    startBackend(root)
    const ok = await waitForBackend(60_000)
    if (!ok) {
      const { dialog } = require('electron')
      dialog.showErrorBox('Inventory Manager',
        'The local server did not start within 60 seconds.\n' +
        `Check ${path.join(root, 'backend', 'desktop-backend.log')}`)
      app.quit()
      return
    }
  }

  await win.loadURL(URL_BASE + '/')
}

app.whenReady().then(createWindow)
app.on('window-all-closed', () => {
  stopBackend()
  app.quit()
})
