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


# W-48 STT 측정 SDK 정리 검증

검증일: 2026-11-15. debug 측정 화면만 변경했다.

- 수정 전: STT 측정 화면을 열고 약 2초 후 Back으로 닫았다. SDK stop과 Activity onDestroy는 있으나 SDK release가 없었다.
- 수정 후 같은 취소 경로: SDK stop → release → `stt cleanup finished` → onDestroy를 확인했다.
- 정상 경로: 무입력 측정 두 번의 제한 시간과 세 번째 듣기 취소를 기다린 뒤 Activity를 닫았다. 약 31.5초에 SDK release와 cleanup이 각각 1회였고 이후 onDestroy에서도 중복되지 않았다.
- `gradlew.bat :engine:test :platform:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug` 통과. W-47 병합 후 통합 구성에서도 재실행했다.
- 실제 환경: Pleos Connect v2.1.0, emulator-5554, com.firstride.qa39. 인식 텍스트는 검증 출력에 포함하지 않았다.

늦게 도착하는 STT 최종 결과의 요청 간 격리, 세 어휘 정확도, 앱의 STT 연결은 이번 수정에 포함하지 않았다.


# W-49 VC HTTP 실패 전달 검증

검증일: 2026-11-15.

- 도구 테스트 11개 통과. 로컬 HTTP 서버와 실제 curl로 성공 시 상태 코드·JSON 보존, 차량 변경/상태 조회 HTTP 500 실패, 세션 실패 시 PUT 미전송, CRS-01 주입 실패 시 즉시 중단을 확인했다. fake adb를 사용하므로 테스트는 차량에 연결하지 않는다.
- `bash -n tools/vc.sh`, `bash -n tools/check-crs01.sh`, `git diff --check` 통과.
- `gradlew.bat :engine:test :platform:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug` 통과.
- 실제 Pleos Connect v2.1.0 / emulator-5554 / com.firstride.qa39 / real 모드에서 `tools/check-crs01.sh`의 AC-CRS01-1·2·2b·2c·3·4 모두 통과했다.
- HTTP 실패의 종료 코드와 신호 주입 실패 중단만 바꿨다. 엔진 판정과 차량 제어 범위는 변경하지 않았다.


# W-50 STT 요청 격리 검증

검증일: 2026-11-15.

- 새 STT 회귀 테스트 14개 통과. 종료→5ms→최종 텍스트, 중간/빈 결과, 취소·시간 초과 뒤 늦은 결과, 중복 결과, 초기화 실패·시간 초과·취소, 요청 예외, stop/release, 동시 호출과 다른 스레드 정리 중 취소를 확인했다.
- `gradlew.bat :engine:test :platform:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug` 통과.
- 실제 SDK 3.3.1 / Pleos Connect v2.1.0 / emulator-5554 / com.firstride.qa39에서 듣는 중 Back: initialize → request → removeListener → release → cleanup → onDestroy 확인.
- 명시적인 음성 입력 없이 연속 측정: SDK 인스턴스 3개의 initialize/request/removeListener/release를 각각 확인했다. 처음 두 요청은 제한 시간, 세 번째는 취소 경로로 정리했다. 초기화→request는 122/2/37ms, 최초 차가운 연결의 별도 샘플은 약 1.3초였다.
- W-48 시점에는 공유 SDK의 release가 전체 측정에서 한 번이었다. W-50부터는 각 요청에서 한 번씩 정리되므로 위 3회가 기대값이다.

인간의 실제 발화 정확도·변형과 양산 차량은 미검증이다. AppViewModel의 stt=null은 유지하며 이 결과를 앱 음성 진행 활성화의 근거로 대신하지 않는다.
