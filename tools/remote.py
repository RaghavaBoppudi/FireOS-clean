#!/usr/bin/env python3
"""A remote control for the TV in your browser, over the same ADB connection as tv.py.

Usage: [TV_HOST=<tv ip>] tools/venv/bin/python -I tools/remote.py [--port 8765] [--lan]
Without TV_HOST it looks for the first device on your network with ADB's port open, and keeps looking until
the TV is on with ADB debugging switched on. Opens http://127.0.0.1:8765: on-screen buttons, arrow keys, Enter, Backspace, typing and a screen preview.
--lan also serves it on your Wi-Fi so a phone can be the remote; anyone on the network can then press buttons.
--no-adb drives a Fire TV paired with firetv.py through Amazon's remote API alone, leaving ADB to the other tools.
"""
import argparse
import json
import os
import queue
import shlex
import socket
import sys
import threading
import time
import webbrowser
from concurrent.futures import ThreadPoolExecutor
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

SEARCHING = "searching"
os.environ.setdefault("TV_HOST", SEARCHING)   # tv.py insists on a host at import; find_tv() fills in the real one
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import tv  # noqa: E402
import firetv  # noqa: E402

PAGE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "remote.html")
KEYS = {"up": 19, "down": 20, "left": 21, "right": 22, "ok": 23, "back": 4, "home": 3, "menu": 82,
        "playpause": 85, "rewind": 89, "forward": 90, "volup": 24, "voldown": 25, "mute": 164, "power": 26}
# what Amazon's remote API understands, for a Fire TV with no ADB yet (see firetv.py)
FIRETV_KEYS = {"up": "dpad_up", "down": "dpad_down", "left": "dpad_left", "right": "dpad_right", "ok": "select",
               "back": "back", "home": "home", "menu": "menu"}


def local_ip():
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
        s.connect(("192.0.2.1", 9))   # no packet is sent; this only picks the interface with the default route
        return s.getsockname()[0]


def find_tv():
    """The first address on this computer's /24 with ADB's port open, or None."""
    me = local_ip()
    prefix = me.rsplit(".", 1)[0]

    def is_open(ip):
        try:
            socket.create_connection((ip, tv.PORT), timeout=0.6).close()
            return ip
        except OSError:
            return None

    with ThreadPoolExecutor(64) as pool:
        hosts = [f"{prefix}.{i}" for i in range(1, 255) if f"{prefix}.{i}" != me]
        return next((ip for ip in pool.map(is_open, hosts) if ip), None)


class Remote:
    """One ADB connection shared by every request, reopened when the TV drops it (sleep, Wi-Fi blip)."""

    def __init__(self):
        self.lock = threading.Lock()
        self.dev = None
        self.status = {"state": SEARCHING} if tv.HOST == SEARCHING else {"state": "connecting", "host": tv.HOST}

    def _run(self, cmd, raw):
        if self.dev is None:
            if tv.HOST == SEARCHING:
                tv.HOST = find_tv() or SEARCHING
                if tv.HOST == SEARCHING:
                    raise OSError("no device on this network has ADB debugging on")
            self.status = {"state": "connecting", "host": tv.HOST}
            self.dev = tv.connect()   # the first time, this waits for "Allow USB debugging?" on the TV
            model = self.dev.shell("getprop ro.product.model").strip()
            self.status = {"state": "connected", "host": tv.HOST, "model": model}
        if raw:
            return self.dev.exec_out(cmd, read_timeout_s=30.0, decode=False)
        return self.dev.shell(cmd, read_timeout_s=30.0)

    def _drop(self, err):
        try:
            self.dev.close()
        except Exception:
            pass
        self.dev = None
        if tv.HOST == SEARCHING:
            self.status = {"state": SEARCHING}
        else:
            self.status = {"state": "error", "host": tv.HOST, "error": str(err) or type(err).__name__}

    def run(self, cmd, raw=False):
        with self.lock:
            stale = self.dev is not None
            try:
                return self._run(cmd, raw)
            except Exception as e:
                self._drop(e)
                if not stale:
                    raise
            # the connection had gone stale; one fresh attempt before giving up
            try:
                return self._run(cmd, raw)
            except Exception as e:
                self._drop(e)
                raise


REMOTE = None
PRESSES = queue.Queue()
NO_ADB = False   # --no-adb: keys go through the Fire TV API only, so tv.py and the scripts keep the one ADB slot


def commands(items):
    """Taps in a row share one `input keyevent`, since starting `input` is the slow part on a stick."""
    taps = []
    for kind, arg in items:
        if kind == "tap":
            taps.append(str(KEYS[arg]))
            continue
        if taps:
            yield "input keyevent " + " ".join(taps)
            taps = []
        if kind == "long":
            yield f"input keyevent --longpress {KEYS[arg]}"
        else:
            yield "input text " + shlex.quote(arg.replace(" ", "%s"))
    if taps:
        yield "input keyevent " + " ".join(taps)


def firetv_mode():
    return (NO_ADB or REMOTE.dev is None) and firetv.token() is not None


def send_firetv(items):
    """No ADB yet: the Fire TV remote API covers the D-pad, OK, Back, Home and Menu, not media keys or typing."""
    try:
        for kind, arg in items:
            if kind != "text" and arg in FIRETV_KEYS:
                firetv.press(FIRETV_KEYS[arg])
    except Exception as e:
        print(f"Fire TV API: {e}", file=sys.stderr)


def worker():
    while True:
        items = [PRESSES.get()]
        while len(items) < 20:   # presses that queued up while the last command ran go out together
            try:
                items.append(PRESSES.get_nowait())
            except queue.Empty:
                break
        if firetv_mode():
            send_firetv(items)
            continue
        try:
            for cmd in commands(items):
                REMOTE.run(cmd)
        except Exception as e:
            print(f"TV unreachable: {e}", file=sys.stderr)
            while not PRESSES.empty():   # don't replay a burst of stale presses once it is back
                PRESSES.get_nowait()


class Handler(BaseHTTPRequestHandler):
    def reply(self, code, body=b"", ctype="text/plain"):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        path = self.path.split("?")[0]
        if path == "/":
            with open(PAGE, "rb") as f:
                self.reply(200, f.read(), "text/html; charset=utf-8")
        elif path == "/status":
            status = {"state": "firetv", "host": tv.HOST} if firetv_mode() else REMOTE.status
            self.reply(200, json.dumps(status).encode(), "application/json")
        elif path == "/screen.png" and NO_ADB:
            self.reply(503, b"screen preview needs ADB; started with --no-adb")
        elif path == "/screen.png":
            try:
                self.reply(200, REMOTE.run("screencap -p", raw=True), "image/png")
            except Exception as e:
                self.reply(502, str(e).encode())
        else:
            self.reply(404, b"not found")

    def do_POST(self):
        # JSON only: a cross-site page can't send this content type without a CORS preflight, which we never answer
        if self.headers.get("Content-Type") != "application/json":
            return self.reply(415, b"expected application/json")
        body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")
        if self.path == "/key" and body.get("key") in KEYS:
            PRESSES.put(("long" if body.get("long") else "tap", body["key"]))
        elif self.path == "/text" and body.get("text"):
            PRESSES.put(("text", str(body["text"])))
        else:
            return self.reply(400, b"bad request")
        self.reply(204)

    def log_message(self, *args):
        pass


def keep_connecting():
    """Retry until the TV answers, so the page can be open before the TV is."""
    last = None
    while True:
        try:
            REMOTE.run("true")
            print(f"Connected to {REMOTE.status['model']} at {tv.HOST}")
            return
        except Exception as e:
            if str(e) != last:
                print(f"Waiting for the TV: {e}", file=sys.stderr)
                last = str(e)
            time.sleep(3)


def main():
    global REMOTE
    ap = argparse.ArgumentParser(description="Browser remote for the TV at $TV_HOST.")
    ap.add_argument("--port", type=int, default=8765)
    ap.add_argument("--lan", action="store_true", help="also serve on your Wi-Fi, e.g. for a phone")
    ap.add_argument("--no-adb", action="store_true",
                    help="Fire TV paired with firetv.py: leave ADB free (Fire OS 5 adbd takes one connection)")
    args = ap.parse_args()

    global NO_ADB
    NO_ADB = args.no_adb
    REMOTE = Remote()
    threading.Thread(target=worker, daemon=True).start()
    if not NO_ADB:
        threading.Thread(target=keep_connecting, daemon=True).start()

    server = ThreadingHTTPServer(("0.0.0.0" if args.lan else "127.0.0.1", args.port), Handler)
    url = f"http://127.0.0.1:{args.port}"
    print(f"Remote at {url}  (Ctrl+C to stop)")
    if args.lan:
        print(f"On your phone: http://{local_ip()}:{args.port}  (anyone on this Wi-Fi can use it)")
    webbrowser.open(url)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
