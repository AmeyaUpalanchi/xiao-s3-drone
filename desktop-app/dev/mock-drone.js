// Fake drone for working on the desktop app without hardware.
//   node dev/mock-drone.js            -> http://127.0.0.1:8042
// In the app, click the address in the sidebar and enter 127.0.0.1:8042.
const http = require('http');

const PORT = Number(process.env.PORT || 8042);
const st = {
  camera: false, sensor: 'none', frame: '', battery_v: 3.94, battery_pct: 78, state: 0,
  height: -1, tof: false, level_pitch: 0.42, level_roll: -0.18,
  ssid: 'ESP-DRONE_7C2C67A1B2C4', channel: 6, fw: process.env.FW || '1.2.3-tdc', ota: true, stream_port: 81,
  imu: true, selftest: true, board: 'XIAO_ESP32S3',
};
let offlineUntil = 0;
let busy = false;

const json = (res, code, obj) => {
  res.writeHead(code, { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
  res.end(JSON.stringify(obj));
};
const wait = (ms) => new Promise((r) => setTimeout(r, ms));
const readBody = (req) => new Promise((r) => { const c = []; req.on('data', (d) => c.push(d)); req.on('end', () => r(Buffer.concat(c))); });

http.createServer(async (req, res) => {
  if (Date.now() < offlineUntil) { req.socket.destroy(); return; }
  const url = new URL(req.url, 'http://x');
  const p = url.pathname;
  console.log(req.method, req.url);
  st.battery_v = +(3.9 + Math.random() * 0.05).toFixed(2);

  if (req.method === 'GET' && p === '/status') return json(res, 200, st);
  if (req.method === 'GET' && p === '/api/wifi') return json(res, 200, { ssid: st.ssid });
  if (req.method !== 'POST') return json(res, 404, { ok: false, error: 'not found' });
  if (st.state !== 0) return json(res, 409, { ok: false, error: 'disarm the drone first' });

  if (p === '/api/motor' || p === '/api/beep') {
    if (busy) return json(res, 409, { ok: false, error: 'busy' });
    busy = true; st.state = 4;
    const m = Number(url.searchParams.get('m') || 0);
    await wait(p === '/api/beep' ? 2400 : m === 0 ? 4000 : 1000);
    busy = false; st.state = 0;
    return json(res, 200, { ok: true });
  }
  if (p === '/api/level') {
    await wait(2000);
    st.level_pitch = +(Math.random() - 0.5).toFixed(2);
    st.level_roll = +(Math.random() - 0.5).toFixed(2);
    return json(res, 200, { ok: true, pitch: st.level_pitch, roll: st.level_roll });
  }
  if (p === '/api/level/reset') { st.level_pitch = 0; st.level_roll = 0; return json(res, 200, { ok: true }); }
  if (p === '/api/wifi') {
    const q = new URLSearchParams((await readBody(req)).toString());
    if (q.has('name')) {   // like the firmware: no name= keeps the current name
      const name = q.get('name');
      st.ssid = name ? 'ESP-DRONE_' + name : 'ESP-DRONE_7C2C67A1B2C4';
    }
    json(res, 200, { ok: true, ssid: st.ssid, restart: true });
    offlineUntil = Date.now() + 4000;
    return;
  }
  if (p === '/api/reboot') { json(res, 200, { ok: true }); offlineUntil = Date.now() + 3000; return; }
  if (p === '/api/ota') {
    const body = await readBody(req);
    if (body[0] !== 0xE9) return json(res, 400, { ok: false, error: 'not an ESP32 firmware file' });
    const project = body.subarray(80, 112).toString('latin1').replace(/\0.*$/s, '');
    if (project !== 'ESPDrone') return json(res, 400, { ok: false, error: 'this is not drone firmware (ESPDrone.bin)' });
    const m = body.toString('latin1').match(/\b(\d+\.\d+\.\d+-tdc(?:-ios)?)\0/);
    if (m) st.fw = m[1];
    json(res, 200, { ok: true, restart: true });
    offlineUntil = Date.now() + 6000;
    return;
  }
  json(res, 404, { ok: false, error: 'not found' });
}).listen(PORT, '127.0.0.1', () => console.log('mock drone on http://127.0.0.1:' + PORT));
