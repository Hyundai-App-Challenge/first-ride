#!/usr/bin/env bash
# Virtual Console(VC)의 HTTP 요청으로 차량 값을 바꾸거나 읽는다. 브라우저 없이 쓸 수 있다.
#
#   tools/vc.sh set hvac ac=true front_defroster=false recirc=true
#   tools/vc.sh set gear gear=Drive
#   tools/vc.sh state            # 전체 상태(JSON)
#   tools/vc.sh state hvac       # 한 영역만
#   tools/vc.sh keepalive 120    # 120초 동안 세션을 살려 둔다(뒤에서 돌린다: tools/vc.sh keepalive 120 &)
#
# VC는 브라우저 세션이 살아 있어야 값을 받는다("emulator_inactive"). 그래서 요청 전에 세션 신호를 한 번 보낸다.
# 주의: 세션이 3초 넘게 끊겼다 다시 열리면 VC가 자기 상태를 차량에 다시 내보내 값이 바뀔 수 있다(순환이 외기로 바뀐 적이 있다).
# 시연·실측 전에는 tools/vc.sh state hvac로 한 번 깨우고 시작 값을 확인한 뒤 진행한다.
# 필드 이름은 GET /vehicle/catalog 의 control.field / field_by_area 에 있다.
set -euo pipefail
cd "$(dirname "$0")/.."
port="${VC_PORT:-8088}"
base="http://127.0.0.1:$port"
# WSL에서는 adb.exe가 연 포트가 Windows 쪽에만 있으므로 Windows의 curl.exe를 쓴다.
curl_bin=curl
[[ -x /mnt/c/Windows/System32/curl.exe ]] && curl_bin=/mnt/c/Windows/System32/curl.exe

tools/adb.sh forward "tcp:$port" "tcp:$port" >/dev/null
"$curl_bin" -s -m 5 -X POST "$base/browser/session?client=firstride-cli" >/dev/null

case "${1:-}" in
  set)
    endpoint="$2"; shift 2
    query=$(IFS='&'; echo "$*")
    "$curl_bin" -s -m 5 -o /dev/null -w '%{http_code}\n' -X PUT "$base/vehicle/$endpoint?$query"
    ;;
  state)
    if [[ -n "${2:-}" ]]; then
      "$curl_bin" -s -m 5 "$base/vehicle/state" | python3 -c "import json,sys; print(json.dumps(json.load(sys.stdin)['$2'], indent=1))"
    else
      "$curl_bin" -s -m 5 "$base/vehicle/state"
    fi
    ;;
  keepalive)
    end=$((SECONDS + ${2:-60}))
    while (( SECONDS < end )); do
      "$curl_bin" -s -m 2 -X POST "$base/browser/session?client=firstride-cli" >/dev/null || true
      sleep 1
    done
    ;;
  *) sed -n '2,11p' "$0"; exit 1 ;;
esac
