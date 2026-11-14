# W-46 QA 도구 검증

검증일: 2026-11-15, Windows PowerShell / Git Bash / Pleos Connect v2.1.0.

| 성공 기준 | 실행 | 결과 |
|---|---|---|
| 패키지·Activity 분리, 기본 패키지 유지 | `BASH_EXE` 지정 후 `python3 -m unittest discover -s tools/tests -v` | 6개 통과. 기본·QA 패키지, real·fake, 측정 Activity, 설치 실패, 잘못된 빌드 인자 확인 |
| 스크립트 구문 | `bash -n`으로 app-target/install/signals/check-crs01 각각 확인 | 통과 |
| 통합 브랜치의 빌드 기준 | `gradlew.bat :engine:test :platform:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug` | BUILD SUCCESSFUL. engine 164, platform 27, app 8개, 실패 0 |
| QA 앱의 실제 SDK 연결 | `APP_ID=com.firstride.qa39 ANDROID_SERIAL=emulator-5554`로 install/debug, signals/real, check-crs01 실행 | AC-CRS01-1·2·2b·2c·3·4 모두 통과. WAITING/PARTIAL/CORRECTION/ALREADY_AT_TARGET/CourseDone 확인 |
| 기존 release 보존 | 실행 전후 `dumpsys package com.firstride`의 설치·갱신 시각 비교 | 동일. 설치 실패 회귀 테스트에서도 uninstall과 후속 실행 없음 |

실제 SDK 검증은 Virtual Console을 통한 에뮬레이터 결과다. 실차와 다른 코스의 전수 검증은 이번 범위에 포함하지 않았다. 로컬 `.qa39` Gradle 변경은 커밋에서 제외한다.
