#!/usr/bin/env bash
# 2차 PT 시연 흐름(사용자 플로우 8절)의 장면별 VC 조작. 시연 전 점검과 녹화에 쓴다(W-43).
#
#   tools/demo-scene.sh reset    # 모든 장면의 시작 상태. 기어 P에서 시작한다(앱은 제한 상태로 시작하므로)
#   tools/demo-scene.sh 3        # 장면 3의 VC 조작을 하고 기대하는 앱 상태를 출력한다
#   tools/demo-scene.sh list     # 장면 목록
#
# 녹화 중에는 다른 창에서 `tools/vc.sh keepalive 1800`을 돌려 둔다.
# VC 세션이 쉬었다 깨어나면 VC가 자기 값을 다시 내보내 상태가 바뀔 수 있다(T3 주의).
set -euo pipefail
cd "$(dirname "$0")/.."
vc() { tools/vc.sh set "$@" >/dev/null; }
say() { printf '  기대: %s\n' "$1"; }

reset() {
  vc gear gear=Park
  vc hvac ac=false recirc=false front_defroster=false rear_defroster=false fan_speed=3 driver_direction=1 driver_temp=22
  vc lights feature=headlamp_mode value=Off
  vc body cabin_light=false front_wiper=Off window_fl=0
  vc turn-signal mode=Off
  echo "reset: 기어 P, 공조(에어컨 끔·외기·제상 끔·풍량 3·풍향 Face·22℃), 외부등 Off, 실내등 끔, 와이퍼 Off, 운전석 창문 닫힘, 방향지시등 끔"
}

case "${1:-}" in
  reset) reset ;;
  list) grep -E '^  [0-9-]+\)' "$0" | sed 's/) .*# /: /' ;;
  1)    say "SCR-01 → SCR-02. 지원되는 코스만 보인다(VC 조작 없음)" ;;                       # 앱 시작
  2)    say "SCR-03 → SCR-04 WAITING, 음성 안내와 위치 안내(앱에서 김서림 제거 시작)" ;;      # 김서림 제거 시작
  3)    vc hvac front_defroster=true; say "PARTIAL \"에어컨이 남았어요\"" ;;                  # 제상만 켬
  3-1)  vc hvac recirc=true; say "CORRECTION, 교정 안내" ;;                                   # 일부러 틀리게 조작
  4)    vc hvac recirc=false ac=true; say "DONE → SCR-05" ;;                                 # 조합을 맞춤
  5)    vc lights feature=headlamp_mode value=LowBeam; say "전조등 코스를 시작하면 ALREADY_AT_TARGET" ;;   # 전조등 코스, 하향등이 켜진 채
  6)    vc lights feature=headlamp_mode value=Off; sleep 2; vc lights feature=headlamp_mode value=LowBeam; say "DONE" ;;   # 껐다 켜기
  7)    say "\"다음\"이라고 말하면 SCR-05 → SCR-03 → SCR-04(와이퍼). STT(W-22·23) 뒤에만" ;;   # 음성으로 다음 코스
  8)    say "\"어디야\"라고 말하면 위치 안내 강조" ;;                                          # 어디야
  9)    vc body front_wiper=Low; say "DONE" ;;                                                # 와이퍼 켬
  10)   vc hvac driver_direction=4; say "\"확인 중\" → DONE(폴링 500ms)" ;;                   # 풍향 단계
  11)   vc body cabin_light=true; say "SCR-06 \"실내등이 켜져 있어요\"" ;;                     # 소등 확인
  12)   vc gear gear=Drive; say "플랫폼 회색 화면(앱은 SCR-08, 음성·구독 멈춤). 끝나면 12-off" ;;   # 주행 전환
  12-off) vc gear gear=Park; say "이어서 하기 / 홈으로를 묻는다" ;;                              # 주행 전환 해제
  13)   say "에뮬레이터에서 재현 방법이 없다(T11). ./gradlew :engine:test 실행 화면으로 대신하고 그렇다고 밝힌다" ;;   # 확인 불가
  *) sed -n '2,9p' "$0"; exit 1 ;;
esac
