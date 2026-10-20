#!/usr/bin/env python3
"""Drive a Fire TV with no remote and no ADB, through the local API Amazon's Fire TV phone app uses.

Set TV_HOST to the stick's IP. Usage:
    firetv.py pair            start the stick's remote service and show a 4-digit PIN on the TV
    firetv.py verify <pin>    pair with that PIN; the token is kept in tools/adbkey/firetv.json
    firetv.py <key>[:N] ...   press keys in order, N times each: dpad_up dpad_down dpad_left dpad_right select back home menu
Enough to reach Settings > My Fire TV > Developer options > ADB debugging, after which tv.py takes over.
remote.py uses it too, until ADB answers.
"""
import json
import os
import ssl
import sys
import time
import urllib.error
import urllib.request

HOST = os.environ.get("TV_HOST")
if not HOST:
    sys.exit("Set TV_HOST to the Fire TV's IP address, e.g.  TV_HOST=192.168.1.50 tools/firetv.py pair")
API = f"https://{HOST}:8080/v1/FireTV"
API_KEY = "0987654321"   # fixed key the Fire TV app sends; the per-device secret is the client token
TOKEN_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "adbkey", "firetv.json")
GAP = float(os.environ.get("FIRETV_GAP", "0.8"))   # seconds between presses; slow sticks drop keys sent faster
TLS = ssl._create_unverified_context()   # the stick serves a self-signed certificate


def wake():
    """The API only listens on 8080 while the FireTVRemote DIAL app runs; it stops when the stick sleeps."""
    req = urllib.request.Request(f"http://{HOST}:8009/apps/FireTVRemote", method="POST")
    urllib.request.urlopen(req, timeout=10).read()
    time.sleep(4)


def token():
    try:
        with open(TOKEN_FILE) as f:
            return json.load(f).get(HOST)
    except (OSError, ValueError):
        return None


def call(path, body=None, retry=True):
    # Key presses must go without a body: even "{}" makes the stick answer 500 with a NullPointerException.
    headers = {"Content-Type": "application/json", "x-api-key": API_KEY}
    if token():
        headers["x-client-token"] = token()
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(API + path, data=data, headers=headers, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=10, context=TLS) as r:
            return json.load(r)
    except urllib.error.HTTPError as e:
        raise RuntimeError(f"{path}: HTTP {e.code} {e.read().decode(errors='replace')}") from None
    except OSError:
        if not retry:
            raise
        wake()
        return call(path, body, retry=False)


def press(key):
    call(f"?action={key}")


def main():
    args = sys.argv[1:]
    if not args:
        sys.exit(__doc__)
    if args[0] == "pair":
        wake()
        call("/pin/display", {"friendlyName": "CleanYourTV"})
        print("A PIN is on the TV now. Run: firetv.py verify <pin>")
    elif args[0] == "verify" and len(args) == 2:
        tok = call("/pin/verify", {"pin": args[1]})["description"]
        os.makedirs(os.path.dirname(TOKEN_FILE), exist_ok=True)
        try:
            with open(TOKEN_FILE) as f:
                saved = json.load(f)
        except (OSError, ValueError):
            saved = {}
        saved[HOST] = tok
        with open(TOKEN_FILE, "w") as f:
            json.dump(saved, f)
        os.chmod(TOKEN_FILE, 0o600)
        print("paired")
    else:
        for arg in args:
            key, _, n = arg.partition(":")
            for _ in range(int(n or 1)):
                press(key)
                time.sleep(GAP)
        print("sent", " ".join(args))


if __name__ == "__main__":
    try:
        main()
    except RuntimeError as e:
        sys.exit(str(e))
