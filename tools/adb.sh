#!/usr/bin/env bash
# adb를 찾아 실행한다. ADB 환경 변수 > PATH의 adb > WSL에서 Windows의 adb.exe 순서.
set -euo pipefail
if [[ -n "${ADB:-}" ]]; then exec "$ADB" "$@"; fi
if command -v adb >/dev/null 2>&1; then exec adb "$@"; fi
for c in /mnt/c/Android/Sdk/platform-tools/adb.exe /mnt/c/Users/*/AppData/Local/Android/Sdk/platform-tools/adb.exe; do
  [[ -x "$c" ]] && exec "$c" "$@"
done
echo "adb를 찾지 못했다. ADB=<경로>로 지정한다." >&2; exit 1
