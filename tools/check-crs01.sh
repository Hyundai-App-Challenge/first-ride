#!/usr/bin/env bash
# CRS-01 수용 기준(AC-CRS01-1~4, 2b, 2c)을 에뮬레이터에서 차례로 확인한다(W-18, L3).
# debug 빌드 + 차량 SDK(tools/signals.sh real)가 설치돼 있어야 한다. 판정 상태는 logcat FR-TRACE에서 읽는다.
# 코스 시작은 홈에서 코스 이름을 누른다(START_TEXT로 바꿀 수 있다).
#
#   tools/check-crs01.sh
set -uo pipefail
cd "$(dirname "$0")/.."
A=tools/adb.sh
START_TEXT="${START_TEXT:-김서림 제거}"
fail=0

tools/vc.sh keepalive 600 &   # VC 세션이 쉬면 VC가 자기 값을 다시 내보내 시작 상태가 바뀐다
hb=$!
trap 'kill $hb 2>/dev/null' EXIT

last_phase() { $A logcat -d -s FR-TRACE:D | grep -oE 'step [0-9]+/[0-9]+ [A-Z_]+|screen CourseDone' | tail -1; }

expect() {  # expect <설명> <기대 문자열>
  local got; got=$(last_phase)
  if [[ "$got" == *"$2"* ]]; then echo "  OK   $1: $got"; else echo "  FAIL $1: 기대 '$2', 실제 '$got'"; fail=1; fi
}

scenario() {  # scenario <이름> <시작 VC 값들>
  echo "### $1"
  tools/vc.sh set gear gear=Park >/dev/null
  tools/vc.sh set hvac $2 >/dev/null
  sleep 1
  $A shell am force-stop com.firstride
  $A logcat -c
  $A shell am start -n com.firstride/.MainActivity >/dev/null
  for _ in $(seq 1 20); do $A logcat -d -s FR-TRACE:D | grep -q 'screen Home' && break; sleep 0.5; done
  tools/tap.sh "$START_TEXT" >/dev/null || { echo "  FAIL 코스를 시작하지 못했다"; fail=1; return; }
  sleep 4
}

step() {  # step <VC 값들> <기대>
  tools/vc.sh set hvac $1 >/dev/null
  sleep 2.5
  expect "$1" "$2"
}

scenario "AC-CRS01-1·2·2b: 에어컨 끔·외기에서 시작" "ac=false recirc=false front_defroster=false"
expect "시작" "WAITING"
step "front_defroster=true" "PARTIAL"          # AC-CRS01-1
step "recirc=true" "CORRECTION"                 # AC-CRS01-2
step "recirc=false ac=true" "screen CourseDone" # AC-CRS01-2b (DONE 뒤 2초 지나 완료 화면)

scenario "AC-CRS01-2·2c: 내기 순환으로 시작" "ac=false recirc=true front_defroster=false"
expect "시작(2c)" "WAITING"
step "front_defroster=true" "CORRECTION"        # AC-CRS01-2

scenario "AC-CRS01-3: 에어컨 켬·외기에서 시작" "ac=true recirc=false front_defroster=false"
step "front_defroster=true" "screen CourseDone"

scenario "AC-CRS01-4: 세 조건이 모두 맞은 채로 시작" "ac=true recirc=false front_defroster=true"
expect "시작" "ALREADY_AT_TARGET"
step "front_defroster=false" "WAITING"
step "front_defroster=true" "screen CourseDone"

[[ $fail == 0 ]] && echo "모두 통과" || echo "실패가 있다"
exit $fail
