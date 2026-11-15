#!/usr/bin/env bash
# 패키지가 달라도 Activity의 코드 네임스페이스는 같다.
app_id="${APP_ID:-com.firstride}"
activity="${ACTIVITY:-com.firstride.MainActivity}"
if [[ "$activity" == .* ]]; then activity="com.firstride$activity"; fi
component="$app_id/$activity"

# Activity 실행·중지도 run-as와 같은 현재 사용자에 한정한다.
current_android_user() {
  local target_user
  target_user=$(tools/adb.sh shell am get-current-user | tr -d '\r') || return
  [[ "$target_user" =~ ^[0-9]+$ ]] || { echo "현재 Android 사용자를 확인할 수 없다" >&2; return 1; }
  printf '%s\n' "$target_user"
}
