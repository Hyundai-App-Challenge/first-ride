#!/usr/bin/env bash
# 기존 CRS-01 명령도 공통 앱/사용자/PID 선택·상태 복원을 사용한다.
# 초기 내기 순환(2c)을 포함한다. START_TEXT/INTRO_START 환경 변수는 유지한다.
set -euo pipefail
cd "$(dirname "$0")/.."
export APP_ID="${APP_ID:-com.firstride}"
exec python3 tools/check-courses.py --course CRS-01 "$@"
