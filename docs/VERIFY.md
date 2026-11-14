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


# W-47 와이퍼 표기 일치 검증

검증일: 2026-11-15. UI/UX Pro Max의 조작부 표기 일관성 원칙을 적용했다. 테마·그림·배치는 동료 PR 범위로 유지한다.

- `gradlew.bat :engine:test :platform:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug` 통과. 와이퍼 기존 AC-CRS04-1 테스트의 안내 표기를 LOW로 맞췄다.
- Pleos Connect v2.1.0 / emulator-5554 / com.firstride.qa39 / real 모드에서 VC의 `front_wiper`를 Off → High → Auto → Low → Off로 변경했다.
- LOW 단계 시작 문구, HIGH·AUTO 교정 문구, OFF 단계 문구를 uiautomator에서 각각 확인했다. WAITING → CORRECTION → CORRECTION → 2/2 WAITING → CourseDone을 확인했다(AC-CRS04-1).
- 검증 환경은 Git Bash이며 `/dev/tty`만 MSYS 경로 변환에서 제외했다. 전역 MSYS_NO_PATHCONV는 Windows curl의 `/dev/null` 쓰기를 막아 종료 코드 23을 만들므로 사용하지 않았다. 이는 검증 명령 환경 설정이며 앱 코드 변경은 없다.
- 실차의 레버 배치와 실제 음성 인식 검증은 이번 범위가 아니다. 기준 차종용 그림은 동료 PR #34·#38에서 보강 중이다.
