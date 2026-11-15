#!/usr/bin/env bash
# APK를 에뮬레이터에 설치하고 실행한다. 사용: tools/install.sh [debug|release]
set -euo pipefail
cd "$(dirname "$0")/.."
source tools/app-target.sh
type="${1:-debug}"
case "$type" in debug|release) ;; *) echo "사용: tools/install.sh [debug|release]" >&2; exit 1 ;; esac
target_user=$(current_android_user)
if [[ "$type" == release ]]; then apk=$(ls apk/*.apk | head -1); else apk=app/build/outputs/apk/debug/app-debug.apk; fi
adb="tools/adb.sh"
# Windows의 adb.exe는 WSL 경로를 읽지 못하므로 Windows 경로로 바꿔 넘긴다.
if command -v wslpath >/dev/null 2>&1 && [[ -z "${ADB:-}" ]] && ! command -v adb >/dev/null 2>&1; then
  tmp="/mnt/c/Users/Public/firstride-install.apk"; cp "$apk" "$tmp"; apk=$(wslpath -w "$tmp")
fi
# 서명이 다르면 기존 앱의 데이터를 보존하고 멈춘다. 삭제 여부는 사용자가 결정한다.
if ! out=$("$adb" install -r -d "$apk" 2>&1); then
  echo "$out" >&2
  echo "설치에 실패했다. 기존 앱은 삭제하지 않았다. APK의 패키지·서명과 APP_ID를 확인한다." >&2
  exit 1
fi
"$adb" shell am start --user "$target_user" -n "$component"
