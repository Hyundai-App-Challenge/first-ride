#!/usr/bin/env bash
# 패키지가 달라도 Activity의 코드 네임스페이스는 같다.
app_id="${APP_ID:-com.firstride}"
activity="${ACTIVITY:-com.firstride.MainActivity}"
if [[ "$activity" == .* ]]; then activity="com.firstride$activity"; fi
component="$app_id/$activity"
