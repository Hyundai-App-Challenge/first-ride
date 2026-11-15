#!/usr/bin/env bash
# 화면에서 글자가 같은 요소를 찾아 누른다(uiautomator). 사용: tools/tap.sh "설정"
# 화면이 바뀌는 중이거나 uiautomator가 바쁘면 못 찾을 수 있어 0.5초 간격으로 최대 10번(약 5초) 다시 본다.
# APP_ID를 주면 해당 앱만 찾고, 없으면 기존처럼 전체 화면에서 찾는다.
set -euo pipefail
cd "$(dirname "$0")/.."
[[ $# == 1 ]] || { echo 'usage: tools/tap.sh "text"' >&2; exit 1; }
find_bounds() {
if ! xml=$(MSYS2_ARG_CONV_EXCL="${MSYS2_ARG_CONV_EXCL:+$MSYS2_ARG_CONV_EXCL;}/dev/tty" tools/adb.sh exec-out uiautomator dump /dev/tty 2>/dev/null); then return 0; fi
python3 - "$1" "${APP_ID:-}" "$xml" <<'PY'
import re, sys, xml.etree.ElementTree as ET
text, package, xml = sys.argv[1:]
start, end = xml.find('<hierarchy'), xml.find('</hierarchy>')
if start < 0 or end < 0:
    sys.exit(0)
try:
    root = ET.fromstring(xml[start:end + 12])
except ET.ParseError:
    sys.exit(0)
for node in root.iter('node'):
    if package and node.get('package') != package:
        continue
    if node.get('enabled') == 'false' or text not in (node.get('text'), node.get('content-desc')):
        continue
    bounds = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.get('bounds', ''))
    if bounds:
        x1, y1, x2, y2 = map(int, bounds.groups())
        if x2 > x1 and y2 > y1:
            print((x1 + x2) // 2, (y1 + y2) // 2)
            break
PY
}
bounds=""
for _ in $(seq 1 10); do
  bounds=$(find_bounds "$1")
  [[ -n "$bounds" ]] && break
  sleep 0.5
done
[[ -n "$bounds" ]] || { echo "not found: $1" >&2; exit 1; }
tools/adb.sh shell input tap $bounds
