#!/usr/bin/env bash
# debug 빌드의 신호원을 고른다. 사용: tools/signals.sh real|fake
#   real: 차량 SDK(VC로 값을 바꾼다)   fake: 가짜 신호원(개발자 패널로 값을 바꾼다, 기본값)
# AAOS에서는 앱이 사용자 10 등 현재 사용자로 돌기 때문에 run-as에 --user를 준다(주지 않으면 사용자 0의 폴더에 쓴다).
set -euo pipefail
cd "$(dirname "$0")/.."
user=$(tools/adb.sh shell am get-current-user | tr -d '\r')
case "${1:-}" in
  real) tools/adb.sh shell run-as com.firstride --user "$user" mkdir -p files
        tools/adb.sh shell run-as com.firstride --user "$user" touch files/real_signals ;;
  fake) tools/adb.sh shell run-as com.firstride --user "$user" rm -f files/real_signals ;;
  *) sed -n '2,4p' "$0"; exit 1 ;;
esac
tools/adb.sh shell am force-stop com.firstride
tools/adb.sh shell am start -n com.firstride/.MainActivity >/dev/null
echo "signals=$1 (user $user)"
