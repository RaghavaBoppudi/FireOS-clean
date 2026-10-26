#!/usr/bin/env bash
# Build and install the home screen on a Fire TV, grant what its watcher needs, and open it.
# Usage:   TV_HOST=192.168.1.50 firehome/install.sh
# Dock:    firehome/install.sh --dock "Netflix,YouTube,Prime Video"     the apps on the dock, by name or package
#          (more than six: the first six by name; a dock the user has edited is kept unless --reset-dock is added)
# Undo:    TV_HOST=192.168.1.50 firehome/install.sh --remove     (Amazon's home is back on the next Home press)
set -euo pipefail
cd "$(dirname "$0")/.."
PY=tools/venv/bin/python
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt"; exit 1; }
tv() { "$PY" -I tools/tv.py "$@"; }

DOCK=
RESET=false
while [ $# -gt 0 ]; do
  case "$1" in
    --dock) DOCK="${2:-}"; shift 2 ;;
    --reset-dock) RESET=true; shift ;;
    *) break ;;
  esac
done
case "$DOCK" in *"'"*) echo "--dock may not contain a quote"; exit 1;; esac

if [ "${1:-}" = "--remove" ]; then
  printf '%s\n' "pm uninstall com.firehome" "input keyevent 3" | tv --batch
  exit 0
fi

firehome/build.sh
"$PY" -I tools/b64push.py firehome/build/firehome.apk /data/local/tmp/firehome.apk
# Fire OS drops accessibility services set over ADB, but grants these: READ_LOGS lets the watcher see Home
# presses, SYSTEM_ALERT_WINDOW lets it cover Amazon's home meanwhile, GET_USAGE_STATS orders the icons by use.
SEND_DOCK=
[ -n "$DOCK" ] && SEND_DOCK="am broadcast -n com.firehome/.DockReceiver --es apps '$DOCK' --ez reset $RESET"
printf '%s\n' \
  "pm install -r /data/local/tmp/firehome.apk" \
  "rm /data/local/tmp/firehome.apk" \
  "pm grant com.firehome android.permission.READ_LOGS" \
  "cmd notification allow_listener com.firehome/com.firehome.BootListener" \
  "appops set com.firehome SYSTEM_ALERT_WINDOW allow" \
  "appops set com.firehome GET_USAGE_STATS allow" \
  "$SEND_DOCK" \
  "am start -n com.firehome/.HomeActivity" | tv --batch
