#!/usr/bin/env bash
# APK를 에뮬레이터에 설치하고 실행한다. 사용: tools/install.sh [debug|release]
set -euo pipefail
cd "$(dirname "$0")/.."
type="${1:-debug}"
if [[ "$type" == release ]]; then apk=$(ls apk/*.apk | head -1); else apk=app/build/outputs/apk/debug/app-debug.apk; fi
adb="tools/adb.sh"
# Windows의 adb.exe는 WSL 경로를 읽지 못하므로 Windows 경로로 바꿔 넘긴다.
if command -v wslpath >/dev/null 2>&1 && [[ -z "${ADB:-}" ]] && ! command -v adb >/dev/null 2>&1; then
  tmp="/mnt/c/Users/Public/firstride-install.apk"; cp "$apk" "$tmp"; apk=$(wslpath -w "$tmp")
fi
"$adb" install -r -d "$apk"
"$adb" shell am start -n com.firstride/.MainActivity
