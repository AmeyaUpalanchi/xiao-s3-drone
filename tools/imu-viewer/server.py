#!/usr/bin/env python3
"""Live orientation viewer for the IMU bench test (CONFIG_DRONE_IMU_BENCH_TEST).

Reads the drone's USB serial output and shows it in the browser: a 3D drone,
pitch / roll / yaw, battery, and a guided tilt test that works out the
sensor-to-drone axis mapping from the raw accelerometer.

  python3 tools/imu-viewer/server.py                  # auto-finds /dev/cu.usbmodem*
  python3 tools/imu-viewer/server.py --serial /dev/cu.usbmodem1101
  python3 tools/imu-viewer/server.py --follow serial.log   # read a log someone else writes

Then open http://localhost:8765. Needs pyserial for --serial
(pip3 install pyserial, or run it from the ESP-IDF Python environment).
Close idf.py monitor first: only one program can open the port.
"""
import argparse
import glob
import json
import os
import re
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HERE = os.path.dirname(os.path.abspath(__file__))
NUM = r'([-+]?\d+(?:\.\d+)?)'
ATT_RE = re.compile(r'pitch\s+' + NUM + r'\s+roll\s+' + NUM + r'\s+yaw\s+' + NUM +
                    r'\s+\|\s+battery\s+' + NUM + r'\s*V' +
                    r'(?:\s+\|\s+raw g X' + NUM + r' Y' + NUM + r' Z' + NUM + r')?')
ANSI_RE = re.compile(r'\x1b\[[0-9;]*m')
IDF_LOG_RE = re.compile(r'^[IWEDV] \(\d+\)')


class Hub:
    """Fan-out of parsed events to every open browser tab."""

    def __init__(self):
        self.cond = threading.Condition()
        self.seq = 0
        self.events = []

    def push(self, ev):
        with self.cond:
            self.seq += 1
            self.events.append((self.seq, ev))
            del self.events[:-400]
            self.cond.notify_all()

    def since(self, after, timeout):
        with self.cond:
            if self.seq <= after:
                self.cond.wait(timeout)
            return [(s, e) for s, e in self.events if s > after], self.seq


hub = Hub()
state = {'serial': None, 'port': None, 'save': None}


def handle_line(raw):
    line = ANSI_RE.sub('', raw).strip()
    if not line:
        return
    m = ATT_RE.search(line)
    if m:
        v = [float(x) if x is not None else None for x in m.groups()]
        ev = {'t': 'att', 'pitch': v[0], 'roll': v[1], 'yaw': v[2], 'vbat': v[3]}
        if v[4] is not None:
            ev['raw'] = v[4:7]
        hub.push(ev)
    elif not IDF_LOG_RE.match(line):
        hub.push({'t': 'msg', 'text': line})


def split_lines(buf, data):
    buf += data
    *lines, rest = buf.split(b'\n')
    for l in lines:
        handle_line(l.decode('utf-8', 'replace'))
    return rest


def follow_file(path):
    while not os.path.exists(path):
        time.sleep(0.5)
    f = open(path, 'rb')
    f.seek(0, os.SEEK_END)
    buf = b''
    while True:
        data = f.read(8192)
        if data:
            buf = split_lines(buf, data)
            continue
        if os.path.getsize(path) < f.tell():   # log was truncated
            f.seek(0)
        time.sleep(0.02)


def read_serial(port):
    import serial
    save = open(state['save'], 'ab', buffering=0) if state['save'] else None
    while True:
        try:
            s = serial.Serial(port, 115200, timeout=0.2)
            state['serial'] = s
            hub.push({'t': 'msg', 'text': 'Connected to ' + port})
            buf = b''
            while True:
                data = s.read(4096)
                if data:
                    if save:
                        save.write(data)
                    buf = split_lines(buf, data)
        except Exception as e:  # unplugged, port busy, ...
            state['serial'] = None
            hub.push({'t': 'msg', 'text': 'Serial %s: %s. Retrying...' % (port, e)})
            time.sleep(1.5)


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def send_body(self, code, ctype, body):
        self.send_response(code)
        self.send_header('Content-Type', ctype)
        self.send_header('Content-Length', str(len(body)))
        self.send_header('Cache-Control', 'no-store')
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path in ('/', '/index.html') or self.path.startswith('/?'):
            with open(os.path.join(HERE, 'index.html'), 'rb') as f:
                self.send_body(200, 'text/html; charset=utf-8', f.read())
        elif self.path == '/info':
            info = {'source': state['port'] or 'log file', 'canReset': state['port'] is not None}
            self.send_body(200, 'application/json', json.dumps(info).encode())
        elif self.path == '/result':
            try:
                with open(args.results, 'rb') as f:
                    self.send_body(200, 'application/json', f.read())
            except FileNotFoundError:
                self.send_body(404, 'application/json', b'{}')
        elif self.path == '/events':
            self.send_response(200)
            self.send_header('Content-Type', 'text/event-stream')
            self.send_header('Cache-Control', 'no-store')
            self.end_headers()
            seq = hub.seq
            last_write = time.time()
            try:
                while True:
                    evs, seq = hub.since(seq, 1.0)
                    for _, ev in evs:
                        self.wfile.write(b'data: ' + json.dumps(ev).encode() + b'\n\n')
                        last_write = time.time()
                    if time.time() - last_write > 5:
                        self.wfile.write(b': keepalive\n\n')
                        last_write = time.time()
                    self.wfile.flush()
            except (BrokenPipeError, ConnectionResetError):
                pass
        else:
            self.send_body(404, 'text/plain', b'not found')

    def do_POST(self):
        body = self.rfile.read(int(self.headers.get('Content-Length', 0) or 0))
        if self.path == '/result':
            try:
                data = json.loads(body or b'{}')
            except ValueError:
                return self.send_body(400, 'text/plain', b'bad json')
            with open(args.results, 'w') as f:
                json.dump(data, f, indent=2)
            self.send_body(200, 'application/json', b'{"saved":true}')
        elif self.path == '/reset':
            s = state['serial']
            if s is None:
                return self.send_body(409, 'application/json', b'{"reset":false}')
            # Same reset pulse esptool uses: the board reboots and recalibrates
            s.dtr = False
            s.rts = True
            time.sleep(0.1)
            s.rts = False
            self.send_body(200, 'application/json', b'{"reset":true}')
        else:
            self.send_body(404, 'text/plain', b'not found')


if __name__ == '__main__':
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--serial', default='auto', help='serial port, or "auto" (default)')
    ap.add_argument('--follow', help='follow this log file instead of opening the serial port')
    ap.add_argument('--save', help='also append the raw serial output to this file')
    ap.add_argument('--results', default=os.path.join(HERE, 'last-tilt-test.json'),
                    help='where the tilt test result is saved')
    ap.add_argument('--http-port', type=int, default=8765)
    args = ap.parse_args()

    if args.follow:
        threading.Thread(target=follow_file, args=(args.follow,), daemon=True).start()
        source = args.follow
    else:
        port = args.serial
        if port == 'auto':
            ports = sorted(glob.glob('/dev/cu.usbmodem*') + glob.glob('/dev/ttyACM*'))
            if not ports:
                raise SystemExit('No drone found on USB. Plug it in or pass --serial PORT.')
            port = ports[0]
        state['port'] = port
        state['save'] = args.save
        threading.Thread(target=read_serial, args=(port,), daemon=True).start()
        source = port

    srv = ThreadingHTTPServer(('127.0.0.1', args.http_port), Handler)
    srv.daemon_threads = True
    print('Reading %s. Open http://localhost:%d' % (source, args.http_port))
    srv.serve_forever()
