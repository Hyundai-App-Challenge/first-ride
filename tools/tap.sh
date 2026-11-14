#!/usr/bin/env bash
# 화면에서 글자가 같은 요소를 찾아 누른다(uiautomator). 사용: tools/tap.sh "설정"
set -euo pipefail
cd "$(dirname "$0")/.."
xml=$(tools/adb.sh exec-out uiautomator dump /dev/tty 2>/dev/null)
bounds=$(python3 - "$1" "$xml" <<'PY'
import re, sys
text, xml = sys.argv[1], sys.argv[2]
for m in re.finditer(r'<node [^>]*>', xml):
    n = m.group(0)
    if f'text="{text}"' in n or f'content-desc="{text}"' in n:
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n).groups()
        x1, y1, x2, y2 = map(int, b); print((x1 + x2) // 2, (y1 + y2) // 2); break
PY
)
[[ -n "$bounds" ]] || { echo "not found: $1" >&2; exit 1; }
tools/adb.sh shell input tap $bounds
