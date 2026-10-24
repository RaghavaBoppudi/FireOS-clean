#!/usr/bin/env bash
# Build and install the YouTube home on a Fire TV, let its watcher read the log, and open it.
# Usage:   TV_HOST=192.168.1.50 firehome/install.sh
# Undo:    TV_HOST=192.168.1.50 firehome/install.sh --remove     (Amazon's home is back on the next Home press)
set -euo pipefail
cd "$(dirname "$0")/.."
PY=tools/venv/bin/python
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt"; exit 1; }
tv() { "$PY" -I tools/tv.py "$@"; }

if [ "${1:-}" = "--remove" ]; then
  printf '%s\n' "pm uninstall com.firehome" "input keyevent 3" | tv --batch
  exit 0
fi

firehome/build.sh
# the APK carries the background photos, which is too much for the ADB push in tv.py; b64push.py is quick and checks it arrived
"$PY" -I tools/b64push.py firehome/build/firehome.apk /data/local/tmp/firehome.apk
# Fire OS drops accessibility services set over ADB, but grants these: READ_LOGS lets the watcher see Home
# presses, SYSTEM_ALERT_WINDOW lets it cover Amazon's home meanwhile, GET_USAGE_STATS orders the icons by use.
printf '%s\n' \
  "pm install -r /data/local/tmp/firehome.apk" \
  "rm /data/local/tmp/firehome.apk" \
  "pm grant com.firehome android.permission.READ_LOGS" \
  "cmd notification allow_listener com.firehome/com.firehome.BootListener" \
  "appops set com.firehome SYSTEM_ALERT_WINDOW allow" \
  "appops set com.firehome GET_USAGE_STATS allow" \
  "am start -n com.firehome/.HomeActivity" | tv --batch
