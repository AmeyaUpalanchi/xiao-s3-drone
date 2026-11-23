import { ESPLoader, Transport } from '../../node_modules/esptool-js/bundle.js';

const $ = (id) => document.getElementById(id);
const tdc = window.tdc;

let drone = null;          // last /status answer, or null when offline
let polling = true;
let latest = null;         // latest GitHub release
let otaFile = null;        // file picked on the firmware page
let usbFile = null;        // file picked on the USB page
let busy = false;          // a motor / level / ota job is running

const STATES = {
  0: 'Disarmed',
  1: 'Armed',
  2: 'Landing',
  3: 'Landing by itself (link or battery)',
  4: 'Running a test',
};

// ------------------------------------------------------------------ helpers

let toastTimer;
function toast(msg, err = false) {
  const t = $('toast');
  t.textContent = msg;
  t.classList.toggle('err', err);
  t.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (t.hidden = true), err ? 6000 : 3500);
}

const kb = (n) => Math.round(n / 1024).toLocaleString() + ' KB';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function cleanVersion(v) { return (v || '').replace(/-tdc.*$/, '').replace(/-ios$/, ''); }
function newer(a, b) {        // is a newer than b
  const pa = cleanVersion(a).split('.').map(Number), pb = cleanVersion(b).split('.').map(Number);
  for (let i = 0; i < 3; i++) if ((pa[i] || 0) !== (pb[i] || 0)) return (pa[i] || 0) > (pb[i] || 0);
  return false;
}
function ago(iso) {
  const d = Math.round((Date.now() - new Date(iso)) / 86400000);
  return d <= 0 ? 'today' : d === 1 ? 'yesterday' : d < 60 ? d + ' days ago' : new Date(iso).toLocaleDateString();
}

function explain(r) {
  if (r.status === 0) return "The drone didn't answer. Are you on its Wi-Fi?";
  if (r.status === 409) return r.error === 'busy' ? 'The drone is busy with another job.' : 'Disarm the drone first.';
  return r.error ? r.error.charAt(0).toUpperCase() + r.error.slice(1) + '.' : 'Something went wrong (' + r.status + ').';
}

function needDrone() {
  if (!drone) { toast('Connect to the drone first.', true); return false; }
  if (drone.state !== 0) { toast('Disarm the drone first.', true); return false; }
  if (busy) { toast('Wait for the current job to finish.', true); return false; }
  return true;
}

// ------------------------------------------------------------------ navigation

function show(page) {
  document.querySelectorAll('nav a').forEach((a) => a.classList.toggle('on', a.dataset.page === page));
  document.querySelectorAll('.page').forEach((p) => p.classList.toggle('on', p.id === 'page-' + page));
  if (page !== 'camera') stopCamera();
}
document.querySelectorAll('nav a, [data-goto]').forEach((a) =>
  a.addEventListener('click', (e) => { e.preventDefault(); show(a.dataset.page || a.dataset.goto); }));

// ------------------------------------------------------------------ status

async function poll() {
  if (polling) {
    const r = await tdc.get('/status');
    setDrone(r.ok && r.json ? r.json : null);
  }
  setTimeout(poll, drone ? 1500 : 2000);
}

function setDrone(s) {
  const was = !!drone;
  drone = s;
  $('link-dot').className = 'dot' + (s ? ' ok' : '');
  $('link-text').textContent = s ? s.ssid : 'Not connected';
  $('link-text').title = s ? s.ssid : '';
  $('offline').hidden = !!s;
  $('online').hidden = !s;

  if (!s) {
    $('drone-title').textContent = 'Drone';
    $('drone-sub').textContent = "Join the drone's Wi-Fi to see it here.";
    $('ota-current').textContent = 'Not connected';
    $('level-now').textContent = '-';
    updateButtons();
    return;
  }

  $('drone-title').textContent = s.ssid;
  $('drone-sub').textContent = STATES[s.state] === 'Disarmed' ? 'Connected. Disarmed and safe to work on.' : 'Connected. ' + (STATES[s.state] || '');

  $('bat-v').textContent = s.battery_v.toFixed(2);
  const pct = Math.max(0, Math.min(100, s.battery_pct));
  const bar = $('bat-bar');
  bar.style.width = pct + '%';
  bar.className = pct <= 15 ? 'danger' : pct <= 25 ? 'warn' : 'ok';
  $('bat-text').textContent = s.battery_v < 2.5 ? 'No battery (running on USB)' : pct + ' % charge' + (pct <= 25 ? ', charge it soon' : '');

  $('f-state').textContent = STATES[s.state] || 'Unknown (' + s.state + ')';
  $('f-fw').textContent = s.fw;
  $('f-ssid').textContent = `${s.ssid}, channel ${s.channel}`;
  $('f-imu').innerHTML = s.imu
    ? (s.selftest ? '<span class="good">OK</span>' : '<span class="bad">Found, but the self-test failed. Keep the drone still while it starts.</span>')
    : '<span class="bad">Not found. Check the GY-521 module.</span>';
  $('f-tof').textContent = s.tof ? `Fitted, reads ${s.height.toFixed(2)} m` : 'None';
  $('f-cam').textContent = s.camera ? `${s.sensor}${s.frame ? ', ' + s.frame : ''}` : 'None';
  const trim = `pitch ${s.level_pitch.toFixed(2)}°, roll ${s.level_roll.toFixed(2)}°`;
  $('f-level').textContent = trim;
  $('level-now').textContent = trim;

  $('ota-current').textContent = s.fw;
  if (!was) {
    // pick the matching build the first time we see the drone
    const ios = /-ios$/.test(s.fw) ? 'ios' : 'android';
    $('ota-build').value = ios;
    $('usb-build').value = ios;
  }
  $('cam-empty').textContent = s.camera ? 'Live view is off' : 'No camera';
  $('cam-sub').textContent = s.camera ? 'Live view from the camera on ' + s.ssid + '.' : 'This drone has no camera. The XIAO ESP32-S3 Sense version has one.';
  showLatest();
  updateButtons();
}

function updateButtons() {
  const ready = !!drone && drone.state === 0 && !busy;
  ['beep-btn', 'reboot-btn', 'motor-all', 'level-btn', 'level-reset', 'wifi-save', 'ota-file-btn'].forEach((id) => ($(id).disabled = !ready));
  $('ota-latest-btn').disabled = !ready || !latest;
  $('cam-toggle').disabled = !drone || !drone.camera;
  $('cam-photo').disabled = !drone || !drone.camera;
  $('quad').classList.toggle('busy', !ready);
}

// ------------------------------------------------------------------ latest release

async function loadLatest() {
  const r = await tdc.latest();
  if (!r.ok) {
    $('ota-latest').textContent = "Couldn't reach GitHub. You can still install a file.";
    $('usb-latest-ver').textContent = '(offline)';
    return;
  }
  latest = r;
  $('usb-latest-ver').textContent = r.version;
  if (r.notes) {
    $('ota-notes').hidden = false;
    $('ota-notes-text').textContent = r.notes.replace(/\r/g, '');
  }
  showLatest();
  updateButtons();
}

function showLatest() {
  if (!latest) return;
  let text = `${latest.version}, released ${ago(latest.published)}`;
  const upToDate = drone && !newer(latest.version, drone.fw);
  if (drone) text += upToDate ? '. The drone is up to date.' : '. Newer than the drone.';
  $('ota-latest').textContent = text;
  $('ota-latest-btn').textContent = (upToDate ? 'Reinstall ' : 'Install ') + latest.version;
  $('ota-latest-btn').classList.toggle('primary', !upToDate);
}

// ------------------------------------------------------------------ firmware over wi-fi

function otaProgress(text, frac) {
  $('ota-progress').hidden = false;
  $('ota-status').textContent = text;
  if (frac != null) $('ota-bar').style.width = Math.round(frac * 100) + '%';
}

async function installOta(file) {
  if (!needDrone()) return;
  if (!file.info || file.info.kind !== 'app' || file.info.project !== 'ESPDrone') {
    toast("That isn't a drone update file. Pick an ESPDrone-…-update.bin.", true);
    return;
  }
  const before = drone.fw;
  busy = true; polling = false; updateButtons();
  $('ota-bar').className = '';
  const off = tdc.onOtaProgress((sent, total) =>
    otaProgress(`Sending ${kb(sent)} of ${kb(total)}`, sent / total * 0.85));
  otaProgress('Starting…', 0);
  const r = await tdc.ota(file.path);
  off();
  if (!r.ok) {
    $('ota-bar').className = 'danger';
    otaProgress('Update failed: ' + explain(r) + ' The drone keeps its old firmware.', 1);
    busy = false; polling = true; updateButtons();
    return;
  }
  otaProgress('Sent. The drone is checking the file and restarting…', 0.9);
  setDrone(null);
  await sleep(4000);
  let back = null;
  for (let i = 0; i < 40 && !back; i++) {
    const s = await tdc.get('/status');
    if (s.ok && s.json) back = s.json; else await sleep(1000);
  }
  busy = false; polling = true;
  if (back) {
    setDrone(back);
    $('ota-bar').className = 'ok';
    otaProgress(back.fw === before
      ? `The drone is back, still on ${back.fw}. It may have rejected the file.`
      : `Done. The drone is now on ${back.fw}.`, 1);
  } else {
    otaProgress("The update was sent but the drone hasn't come back yet. Rejoin its Wi-Fi; your computer may have switched networks while it restarted.", 1);
  }
  updateButtons();
}

$('ota-latest-btn').addEventListener('click', async () => {
  if (!latest || !needDrone()) return;
  const ios = $('ota-build').value === 'ios';
  otaProgress(`Downloading ${latest.version} from GitHub…`, 0.02);
  $('ota-latest-btn').disabled = true;
  const r = await tdc.firmware(latest.version, ios);
  if (!r.ok) {
    otaProgress('Download failed: ' + r.error, 0);
    updateButtons();
    return;
  }
  installOta(r.update);
});

function setOtaFile(f) {
  otaFile = f;
  $('ota-file-row').hidden = !f;
  if (!f) return;
  const ok = f.info && f.info.kind === 'app' && f.info.project === 'ESPDrone';
  $('ota-file-text').innerHTML = '';
  $('ota-file-text').textContent = f.name;
  $('ota-file-info').innerHTML = ok
    ? `Version ${f.info.version}, built ${f.info.date}, ${kb(f.size)}`
    : '<span class="bad">This is not a drone update file.' + (f.info && f.info.kind === 'full' ? ' Full images go on the USB flash page.' : '') + '</span>';
  $('ota-file-btn').hidden = !ok;
}
$('ota-pick').addEventListener('click', async (e) => { e.preventDefault(); const f = await tdc.pickFile('app'); if (f) setOtaFile(f); });
$('ota-file-btn').addEventListener('click', () => otaFile && installOta(otaFile));

const drop = $('ota-drop');
drop.addEventListener('dragover', (e) => { e.preventDefault(); drop.classList.add('over'); });
drop.addEventListener('dragleave', () => drop.classList.remove('over'));
drop.addEventListener('drop', async (e) => {
  e.preventDefault(); drop.classList.remove('over');
  const file = e.dataTransfer.files[0];
  if (file) setOtaFile(await tdc.describeFile(tdc.pathOf(file)));
});
// don't let a stray drop navigate the window
window.addEventListener('dragover', (e) => e.preventDefault());
window.addEventListener('drop', (e) => e.preventDefault());

// ------------------------------------------------------------------ usb flash

const usbLog = (line) => { const p = $('usb-log'); p.textContent += line + '\n'; p.scrollTop = p.scrollHeight; };
function usbProgress(text, frac, cls = '') {
  $('usb-progress').hidden = false;
  $('usb-status').textContent = text;
  $('usb-bar').className = cls;
  if (frac != null) $('usb-bar').style.width = Math.round(frac * 100) + '%';
}

$('usb-pick').addEventListener('click', async (e) => {
  e.preventDefault();
  const f = await tdc.pickFile('full');
  if (!f) return;
  if (!f.info || f.info.kind !== 'full') {
    toast(f.info && f.info.kind === 'app'
      ? 'That is an over-the-air update. For USB pick the …-full.bin file.'
      : "That doesn't look like a full drone image.", true);
    return;
  }
  usbFile = f;
  $('usb-file-name').textContent = `${f.name} (${f.info.version})`;
  document.querySelector('input[name=usb-src][value=file]').checked = true;
});

$('usb-go').addEventListener('click', async () => {
  if (!navigator.serial) { toast('This computer has no serial support.', true); return; }
  const src = document.querySelector('input[name=usb-src]:checked').value;
  let file;
  $('usb-go').disabled = true;
  $('usb-log').textContent = '';
  try {
    if (src === 'file') {
      if (!usbFile) throw new Error('Choose a file first.');
      file = usbFile;
    } else {
      if (!latest) throw new Error("Couldn't reach GitHub. Choose a file instead.");
      usbProgress(`Downloading ${latest.version} from GitHub…`, 0.01);
      const r = await tdc.firmware(latest.version, $('usb-build').value === 'ios');
      if (!r.ok) throw new Error('Download failed: ' + r.error);
      file = r.full;
    }
    usbLog(`Image: ${file.name}, ${file.info.version}, ${kb(file.size)}`);

    usbProgress('Looking for the board…', 0.02);
    let port;
    try {
      port = await navigator.serial.requestPort({ filters: [{ usbVendorId: 0x303a }] });
    } catch {
      throw new Error("No board found. Check the cable is a data cable, or hold BOOT while plugging in.");
    }
    const transport = new Transport(port, false);
    const terminal = { clean() {}, writeLine: usbLog, write: (d) => usbLog(String(d).trimEnd()) };
    const loader = new ESPLoader({ transport, baudrate: 460800, romBaudrate: 115200, terminal });
    usbProgress('Connecting…', 0.03);
    const chip = await loader.main();
    usbLog('Chip: ' + chip);
    if (!/ESP32-S3/i.test(chip)) throw new Error(`This board is a ${chip}, not the XIAO ESP32-S3.`);

    const data = new Uint8Array(await tdc.readFile(file.path));
    usbProgress('Writing…', 0.05);
    await loader.writeFlash({
      fileArray: [{ data: loader.ui8ToBstr(data), address: 0x0 }],
      flashSize: 'keep', flashMode: 'keep', flashFreq: 'keep',
      eraseAll: false, compress: true,
      reportProgress: (_i, written, total) => usbProgress(`Writing ${Math.round(written / total * 100)} %`, 0.05 + written / total * 0.93),
    });
    usbProgress('Restarting the board…', 0.99);
    await loader.after('hard_reset');
    await transport.disconnect();
    usbProgress(`Done. ${file.info.version} is on the drone. Unplug USB, then connect the battery.`, 1, 'ok');
  } catch (err) {
    usbLog('Error: ' + (err && err.message));
    usbProgress(err.message || String(err), 1, 'danger');
  } finally {
    $('usb-go').disabled = false;
  }
});

// ------------------------------------------------------------------ bench tools

async function job(fn) {
  if (!needDrone()) return;
  busy = true; updateButtons();
  try { await fn(); } finally { busy = false; updateButtons(); }
}

document.querySelectorAll('.motor').forEach((g) => g.addEventListener('click', () => job(async () => {
  g.classList.add('spin');
  const r = await tdc.post('/api/motor?m=' + g.dataset.m, '', 6000);
  g.classList.remove('spin');
  if (!r.ok) toast(explain(r), true);
})));

$('motor-all').addEventListener('click', () => job(async () => {
  const order = ['1', '2', '3', '4'];
  const els = order.map((m) => document.querySelector(`.motor[data-m="${m}"]`));
  // light each motor roughly in time with the drone (0.7 s on, 0.3 s off)
  let i = 0;
  els[0].classList.add('spin');
  const t = setInterval(() => { els[i].classList.remove('spin'); i++; if (i < 4) els[i].classList.add('spin'); else clearInterval(t); }, 1000);
  const r = await tdc.post('/api/motor?m=0', '', 10000);
  clearInterval(t); els.forEach((e) => e.classList.remove('spin'));
  if (!r.ok) toast(explain(r), true);
}));

$('beep-btn').addEventListener('click', () => job(async () => {
  const r = await tdc.post('/api/beep', '', 8000);
  if (!r.ok) toast(explain(r), true); else toast('Beeped. Listen for it.');
}));

$('level-btn').addEventListener('click', () => job(async () => {
  $('level-msg').textContent = 'Measuring. Keep it still…';
  const r = await tdc.post('/api/level', '', 10000);
  $('level-msg').textContent = r.ok
    ? `Saved: pitch ${r.json.pitch.toFixed(2)}°, roll ${r.json.roll.toFixed(2)}°.`
    : 'Not saved: ' + explain(r);
}));
$('level-reset').addEventListener('click', () => job(async () => {
  const r = await tdc.post('/api/level/reset', '');
  $('level-msg').textContent = r.ok ? 'Trim cleared.' : explain(r);
}));

$('reboot-btn').addEventListener('click', () => job(async () => {
  const r = await tdc.post('/api/reboot', '');
  if (r.ok) toast('Restarting. It will be back in a few seconds.'); else toast(explain(r), true);
}));

// ------------------------------------------------------------------ wi-fi

$('wifi-form').addEventListener('submit', (e) => {
  e.preventDefault();
  const name = $('wifi-name').value.trim();
  const pass = $('wifi-pass').value;
  if (!/^[A-Za-z0-9_-]{0,20}$/.test(name)) { $('wifi-msg').textContent = 'Use letters, numbers, - and _ only.'; return; }
  if (pass && (pass.length < 8 || pass.length > 63)) { $('wifi-msg').textContent = 'The password needs 8 to 63 characters.'; return; }
  job(async () => {
    const body = 'name=' + encodeURIComponent(name) + (pass ? '&password=' + encodeURIComponent(pass) : '');
    const r = await tdc.post('/api/wifi', body);
    if (!r.ok) { $('wifi-msg').textContent = explain(r); return; }
    $('wifi-msg').textContent = `Saved. The drone is restarting as ${name ? 'ESP-DRONE_' + name : 'ESP-DRONE_<its id>'}. Join that network${pass ? ' with the new password' : ''}.`;
    $('wifi-pass').value = '';
  });
});

// ------------------------------------------------------------------ camera

let camOn = false;
async function startCamera() {
  const s = await tdc.settings();
  const host = s.host.split(':')[0];
  const port = (drone && drone.stream_port) || 81;
  $('cam-img').src = `http://${host}:${port}/stream?t=${Date.now()}`;
  $('cam').classList.add('live');
  $('cam-toggle').textContent = 'Stop live view';
  camOn = true;
}
function stopCamera() {
  if (!camOn) return;
  $('cam-img').removeAttribute('src');
  $('cam').classList.remove('live');
  $('cam-toggle').textContent = 'Start live view';
  camOn = false;
}
$('cam-toggle').addEventListener('click', () => (camOn ? stopCamera() : startCamera()));
$('cam-img').addEventListener('error', () => { if (camOn) { stopCamera(); $('cam-msg').textContent = 'The stream stopped.'; } });
$('cam-photo').addEventListener('click', async () => {
  $('cam-msg').textContent = 'Taking a photo…';
  const r = await tdc.capture();
  $('cam-msg').textContent = r.ok ? 'Saved to ' + r.path : r.canceled ? '' : "Couldn't take a photo: " + r.error;
});

// ------------------------------------------------------------------ address

$('host-btn').addEventListener('click', async () => {
  const s = await tdc.settings();
  $('host-input').value = s.host;
  $('host-dialog').showModal();
});
$('host-dialog').addEventListener('close', async () => {
  const v = $('host-dialog').returnValue;
  if (v === 'cancel') return;
  const host = v === 'default' ? '192.168.43.42' : $('host-input').value.trim();
  if (!/^[\w.-]+(:\d+)?$/.test(host)) { toast('That address looks wrong.', true); return; }
  await tdc.saveSettings({ host });
  $('host-btn').textContent = host;
  setDrone(null);
});

// ------------------------------------------------------------------ start

(async () => {
  const s = await tdc.settings();
  $('host-btn').textContent = s.host;
  updateButtons();
  if (location.hash) show(location.hash.slice(1));
  poll();
  loadLatest();
})();
