#!/usr/bin/env bash
# 화면에서 글자가 같은 요소를 찾아 누른다(uiautomator). 사용: tools/tap.sh "설정"
# 화면이 바뀌는 중이거나 uiautomator가 바쁘면 못 찾을 수 있어 0.5초 간격으로 최대 10번(약 5초) 다시 본다.
set -euo pipefail
cd "$(dirname "$0")/.."
find_bounds() {
xml=$(tools/adb.sh exec-out uiautomator dump /dev/tty 2>/dev/null)
python3 - "$1" "$xml" <<'PY'
import re, sys
text, xml = sys.argv[1], sys.argv[2]
for m in re.finditer(r'<node [^>]*>', xml):
    n = m.group(0)
    if f'text="{text}"' in n or f'content-desc="{text}"' in n:
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n).groups()
        x1, y1, x2, y2 = map(int, b); print((x1 + x2) // 2, (y1 + y2) // 2); break
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
