// TDC Drone desktop app: main process.
// Talks to the drone's web API (http://192.168.43.42), uploads firmware over Wi-Fi,
// fetches releases from GitHub and lets the window use Web Serial for USB flashing.
const { app, BrowserWindow, ipcMain, dialog, shell, session } = require('electron');
const path = require('path');
const fs = require('fs');
const http = require('http');
const https = require('https');
const zlib = require('zlib');

const REPO = 'AmeyaUpalanchi/xiao-s3-drone';
const ESPRESSIF_VID = '303a';
let win;

// ------------------------------------------------------------------ settings

const settingsFile = () => path.join(app.getPath('userData'), 'settings.json');
function loadSettings() {
  try { return { host: '192.168.43.42', ...JSON.parse(fs.readFileSync(settingsFile(), 'utf8')) }; }
  catch { return { host: '192.168.43.42' }; }
}
function saveSettings(s) {
  fs.mkdirSync(path.dirname(settingsFile()), { recursive: true });
  fs.writeFileSync(settingsFile(), JSON.stringify(s, null, 2));
}
let settings;

// ------------------------------------------------------------------ drone http

function droneRequest(method, urlPath, { body = null, type = null, timeout = 3000, onProgress } = {}) {
  return new Promise((resolve) => {
    const [host, port] = settings.host.split(':');
    const headers = {};
    if (body != null) {
      headers['Content-Type'] = type || 'application/x-www-form-urlencoded';
      headers['Content-Length'] = Buffer.byteLength(body);
    }
    const req = http.request({ host, port: port || 80, path: urlPath, method, headers, timeout }, (res) => {
      const chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('end', () => {
        const buf = Buffer.concat(chunks);
        const ctype = res.headers['content-type'] || '';
        let json = null;
        if (ctype.includes('json')) { try { json = JSON.parse(buf.toString('utf8')); } catch {} }
        resolve({ ok: res.statusCode >= 200 && res.statusCode < 300, status: res.statusCode, json,
                  data: ctype.includes('json') ? null : buf, error: json && json.error });
      });
    });
    req.on('timeout', () => req.destroy(new Error('timeout')));
    req.on('error', (e) => resolve({ ok: false, status: 0, error: e.message === 'timeout' ? 'no answer' : e.code || e.message }));
    if (body != null) {
      if (onProgress) {
        // write in pieces so the window can show progress
        let sent = 0;
        const step = 8192;
        const total = body.length;
        const pump = () => {
          while (sent < total) {
            const piece = body.subarray(sent, Math.min(sent + step, total));
            sent += piece.length;
            onProgress(sent, total);
            if (!req.write(piece)) { req.once('drain', pump); return; }
          }
          req.end();
        };
        pump();
      } else {
        req.end(body);
      }
    } else {
      req.end();
    }
  });
}

// ------------------------------------------------------------------ firmware files

// Read the ESP-IDF app description from an app image (ESPDrone.bin) or a merged full image.
function readFirmwareInfo(buf) {
  const parseAt = (off) => {
    if (buf.length < off + 160 || buf[off] !== 0xE9) return null;
    const d = off + 32;
    if (buf.readUInt32LE(d) !== 0xABCD5432) return null;
    const str = (o, n) => buf.subarray(d + o, d + o + n).toString('latin1').replace(/\0.*$/s, '');
    // The app header holds the git hash; the release version is the DRONE_FW_VERSION string.
    const m = buf.toString('latin1').match(/\b(\d+\.\d+\.\d+-tdc(?:-ios)?)\0/);
    return { version: m ? m[1] : str(16, 32), build: str(16, 32), project: str(48, 32),
             time: str(80, 16), date: str(96, 16), idf: str(112, 32) };
  };
  const app = parseAt(0);
  if (app) return { kind: 'app', ...app };
  const full = parseAt(0x20000);
  if (full && buf[0] === 0xE9) return { kind: 'full', ...full };
  return null;
}

function describeFile(filePath) {
  const buf = fs.readFileSync(filePath);
  const info = readFirmwareInfo(buf);
  return { path: filePath, name: path.basename(filePath), size: buf.length, info };
}

// Minimal zip reader (stored + deflate), enough for our release zips.
function unzip(buf) {
  const out = {};
  let eocd = buf.length - 22;
  while (eocd >= 0 && buf.readUInt32LE(eocd) !== 0x06054b50) eocd--;
  if (eocd < 0) throw new Error('not a zip file');
  const count = buf.readUInt16LE(eocd + 10);
  let p = buf.readUInt32LE(eocd + 16);
  for (let i = 0; i < count; i++) {
    const method = buf.readUInt16LE(p + 10);
    const csize = buf.readUInt32LE(p + 20);
    const nlen = buf.readUInt16LE(p + 28), xlen = buf.readUInt16LE(p + 30), clen = buf.readUInt16LE(p + 32);
    const local = buf.readUInt32LE(p + 42);
    const name = buf.subarray(p + 46, p + 46 + nlen).toString('utf8');
    const lstart = local + 30 + buf.readUInt16LE(local + 26) + buf.readUInt16LE(local + 28);
    const data = buf.subarray(lstart, lstart + csize);
    if (!name.endsWith('/')) out[name] = method === 8 ? zlib.inflateRawSync(data) : Buffer.from(data);
    p += 46 + nlen + xlen + clen;
  }
  return out;
}

function httpsGet(url, { json = false, redirects = 5 } = {}) {
  return new Promise((resolve, reject) => {
    https.get(url, { headers: { 'User-Agent': 'tdc-drone-desktop', Accept: json ? 'application/vnd.github+json' : '*/*' } }, (res) => {
      if ([301, 302, 303, 307, 308].includes(res.statusCode) && res.headers.location && redirects > 0) {
        res.resume();
        return resolve(httpsGet(res.headers.location, { json, redirects: redirects - 1 }));
      }
      if (res.statusCode !== 200) { res.resume(); return reject(new Error('GitHub answered ' + res.statusCode)); }
      const chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('end', () => {
        const buf = Buffer.concat(chunks);
        resolve(json ? JSON.parse(buf.toString('utf8')) : buf);
      });
    }).on('error', reject);
  });
}

async function latestRelease() {
  const rel = await httpsGet(`https://api.github.com/repos/${REPO}/releases/latest`, { json: true });
  return {
    version: rel.tag_name.replace(/^v/, ''),
    published: rel.published_at,
    url: rel.html_url,
    notes: rel.body || '',
    assets: rel.assets.map((a) => ({ name: a.name, url: a.browser_download_url, size: a.size })),
  };
}

// Download a release's firmware zip and keep the update and full images in the cache.
async function downloadFirmware(version, ios) {
  const name = `TheDroneCommunity-firmware-${version}${ios ? '-ios' : ''}`;
  const dir = path.join(app.getPath('userData'), 'firmware', name);
  const update = path.join(dir, `ESPDrone-${version}${ios ? '-ios' : ''}-update.bin`);
  const full = path.join(dir, `${name}-full.bin`);
  if (!fs.existsSync(update) || !fs.existsSync(full)) {
    const zip = await httpsGet(`https://github.com/${REPO}/releases/download/v${version}/${name}.zip`);
    const files = unzip(zip);
    fs.mkdirSync(dir, { recursive: true });
    for (const [n, data] of Object.entries(files)) {
      const base = path.basename(n);
      if (base.endsWith('-update.bin') || base.endsWith('-full.bin')) fs.writeFileSync(path.join(dir, base), data);
    }
  }
  return { update: describeFile(update), full: describeFile(full) };
}

// ------------------------------------------------------------------ ipc

function handlers() {
  ipcMain.handle('settings:get', () => settings);
  ipcMain.handle('settings:set', (_e, s) => { settings = { ...settings, ...s }; saveSettings(settings); return settings; });

  ipcMain.handle('drone:get', (_e, p) => droneRequest('GET', p, { timeout: 2500 }));
  ipcMain.handle('drone:post', (_e, p, body, timeout) => droneRequest('POST', p, { body: body ?? '', timeout: timeout || 8000 }));

  ipcMain.handle('drone:ota', async (e, filePath) => {
    const body = fs.readFileSync(filePath);
    const info = readFirmwareInfo(body);
    if (!info || info.kind !== 'app') return { ok: false, error: 'That file is not a drone update (ESPDrone-…-update.bin).' };
    let last = 0;
    return droneRequest('POST', '/api/ota', {
      body, type: 'application/octet-stream', timeout: 60000,
      onProgress: (sent, total) => {
        const now = Date.now();
        if (now - last > 80 || sent === total) { last = now; e.sender.send('ota:progress', sent, total); }
      },
    });
  });

  ipcMain.handle('drone:capture', async () => {
    const r = await droneRequest('GET', '/capture', { timeout: 6000 });
    if (!r.ok || !r.data) return { ok: false, error: r.error || 'no photo' };
    const stamp = new Date().toISOString().slice(0, 19).replace(/[T:]/g, '-');
    const { canceled, filePath } = await dialog.showSaveDialog(win, {
      defaultPath: path.join(app.getPath('pictures'), `drone-${stamp}.jpg`),
      filters: [{ name: 'JPEG', extensions: ['jpg'] }],
    });
    if (canceled) return { ok: false, canceled: true };
    fs.writeFileSync(filePath, r.data);
    return { ok: true, path: filePath };
  });

  ipcMain.handle('file:pick', async (_e, kind) => {
    const { canceled, filePaths } = await dialog.showOpenDialog(win, {
      title: kind === 'full' ? 'Choose a full firmware image' : 'Choose a firmware update',
      filters: [{ name: 'Firmware', extensions: ['bin'] }],
      properties: ['openFile'],
    });
    return canceled ? null : describeFile(filePaths[0]);
  });
  ipcMain.handle('file:describe', (_e, p) => describeFile(p));
  ipcMain.handle('file:read', (_e, p) => fs.readFileSync(p));

  ipcMain.handle('gh:latest', async () => {
    try { return { ok: true, ...(await latestRelease()) }; }
    catch (err) { return { ok: false, error: err.message }; }
  });
  ipcMain.handle('gh:firmware', async (_e, version, ios) => {
    try { return { ok: true, ...(await downloadFirmware(version, ios)) }; }
    catch (err) { return { ok: false, error: err.message }; }
  });

  ipcMain.handle('open:external', (_e, url) => {
    if (/^https:\/\/(github\.com|apps\.apple\.com)\//.test(url)) shell.openExternal(url);
  });
  ipcMain.handle('open:path', (_e, p) => shell.showItemInFolder(p));
}

// ------------------------------------------------------------------ window

function createWindow() {
  win = new BrowserWindow({
    width: 1120, height: 740, minWidth: 920, minHeight: 600,
    backgroundColor: '#111315',
    title: 'TDC Drone',
    titleBarStyle: process.platform === 'darwin' ? 'hiddenInset' : 'default',
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, sandbox: false },
  });

  // Web Serial: pick the XIAO (Espressif USB) by itself, otherwise the first port.
  win.webContents.session.on('select-serial-port', (event, portList, _wc, callback) => {
    event.preventDefault();
    const esp = portList.find((p) => (p.vendorId || '').toLowerCase() === ESPRESSIF_VID);
    callback(esp ? esp.portId : (portList[0] ? portList[0].portId : ''));
  });
  win.webContents.session.setPermissionCheckHandler((_wc, permission) => permission === 'serial');
  win.webContents.session.setDevicePermissionHandler((d) => d.deviceType === 'serial');

  win.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  win.loadFile(path.join(__dirname, 'renderer', 'index.html'), { hash: process.env.TDC_PAGE || '' });

  // Dev only: TDC_SHOT=out.png saves a screenshot after a few seconds and quits.
  if (process.env.TDC_SHOT) {
    win.webContents.once('did-finish-load', () => setTimeout(async () => {
      if (process.env.TDC_JS) await win.webContents.executeJavaScript(process.env.TDC_JS).catch(() => {});
      setTimeout(async () => {
        const img = await win.webContents.capturePage();
        fs.writeFileSync(process.env.TDC_SHOT, img.toPNG());
        app.quit();
      }, Number(process.env.TDC_WAIT || 1500));
    }, 3500));
  }
}

app.whenReady().then(() => {
  settings = loadSettings();
  if (process.env.TDC_HOST) settings.host = process.env.TDC_HOST;
  handlers();
  createWindow();
  app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });
});
app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });
