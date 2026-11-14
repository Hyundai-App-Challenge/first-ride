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


# W-51 중단한 실습의 확인·재개 검증

검증일: 2026-11-15. Pleos Connect v2.1.0 / emulator-5554 / com.firstride.qa39 / real 신호.

| 요구사항 | 실행 | 결과 |
|---|---|---|
| 같은 단계 재개·신호 재조회 | CourseRunner 회귀 테스트, 실제 VC 와이퍼 LOW→2단계→D→OFF→P | 2/2단계 확인 후 재개는 ALREADY_AT_TARGET. LOW→OFF로 새로 조작한 뒤 완료 |
| 주행 중 차단·사용자 선택 | 확인창 표시 중 D→P, 백그라운드→복귀, 홈으로 선택 | 주행 중 확인창 없음, P에서 다시 표시. 홈 선택 후 구독 active=0 |
| 이전 건너뜀 보존 | 1단계 건너뛰기→백그라운드→재개→2단계 완료 | 완료 화면에 건너뜀 있음 표시. 엔진 저장값 HAS_SKIPPED |
| 취소 후 구독·실행 직렬화 | CourseRunner 동시 실행 회귀 테스트 | 3개→3개→0개. 두 실행의 구독이 겹치지 않음 |
| 완료 후 새 시도 | CourseRunner 재실행 회귀 테스트 | 1단계부터 초기 상태 재조회 |
| 유효 시간 | 기존 InterruptionTest 경계 테스트 | 정확히 30분은 허용, 1ms 초과는 거부. 실제 30분 대기는 미실행 |
| 테마·접근성 | 낮/밤, font_scale=2.0 설정 후 화면 캡처 | 확인 내용·버튼이 화면 안에 표시. 설정은 캡처 후 복원 |

신규 회귀 테스트는 4개다. 첫 실행의 동시 구독 테스트가 fixture의 3개 신호를 1개로 오인해 실패했으며, fixture를 확인하고 신규 테스트의 기대값만 3개로 수정해 통과했다. 실차·프로세스 강제 종료 후 복구는 이번 범위가 아니다. 재개 정보는 의도적으로 영속 저장하지 않는다.

최종 `gradlew.bat :engine:test :platform:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug` 통과(engine 168, platform 41, app 8, 실패 0). 캡처 뒤 남은 상태를 전제로 한 재실행은 확인창을 찾지 못해 중단했다. 앱 설치·신호원 지정부터 다시 실행한 재개→완료→건너뜀 보존→홈 복귀 전체 경로는 모두 통과했다.


# W-52 STT 실측 원문 비기록 검증

검증일: 2026-11-15. 개발용 측정 로그도 POL-V-14/POL-S-2를 따르도록 결과 종류만 표시한다.

- debug 전용 회귀 테스트 2개 통과. 임의 문장·줄바꿈·빈 문자열의 결과는 항상 `Text`, 무음/오류는 `Silence`/`Error`다.
- `gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:compileReleaseKotlin` 통과(app 10개, 실패 0). 새 요약 함수와 테스트는 debug 소스에만 있다.
- Pleos Connect v2.1.0 / emulator-5554 / com.firstride.qa39에서 실제 STT 측정: #1 Error 15990ms, #2 Error 15007ms, #3 1500ms 후 취소와 cleanup 확인. 인식 원문을 출력하는 두 호출 모두 요약 함수를 사용한다.

실제 발화의 Text 경로는 이번 실행에서 만들지 않았으며, 임의 원문 비노출은 단위 테스트로 확인했다. 외부 SDK가 자체적으로 남기는 로그의 변경·비저장 보장과 세 어휘 정확도 검증은 이 수정에 포함하지 않는다.


# W-53 원상 복귀 안내 미응답 제한 검증

검증일: 2026-11-15.

| 요구사항 | 실행 | 결과 |
|---|---|---|
| 미응답 조회 제한·다른 신호 보존 | RestoreTest에 한 신호 무응답 주입 | 모든 조회가 동시에 시작되고 3초에 정상 기준 값만 반환. 대기 작업 0개 |
| 최초 관찰 미응답·늦은 값 | 무응답 Flow 뒤 실제 ON 전송 | 3초 후 해당 신호만 unverified, 실제 값 후 changed로 갱신 |
| 취소 정리·빈 종료 | 조회 취소, 첫 값 전 관찰 취소, emptyFlow | 남은 작업/구독 0개, 빈 응답을 복구 완료로 단정하지 않음 |
| 정상 신호 유지 | 초기 정상 값 뒤 가상 시간 60초 | 값이 같아도 TIMEOUT으로 바꾸지 않으며 다음 변경도 반영 |
| 전체 통합 구성 | `gradlew.bat :engine:test :platform:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug` | engine 174, platform 41, app 10개 통과, debug 빌드 성공 |
| 실제 원상 복귀 | Pleos Connect v2.1.0 / emulator-5554 / com.firstride.qa39 / real | CRS-01 완료 후 제상·에어컨 목록, VC로 원래 OFF 복구 후 목록 제거, 홈 복귀 후 active=0 |

신규 회귀 테스트는 6개다. 수정 전 첫 4개를 실행했을 때 병렬 조회·최초 관찰 미응답 두 테스트가 실패했고, 수정 후 기존 5개와 신규 6개 모두 통과했다. SDK 미응답은 단위 테스트로 주입했으며 실제 에뮬레이터 SDK 장애·실차는 재현하지 않았다. 실제 SDK 검증 후 QA 신호원을 fake로 복구했다.


# W-54 소등 확인의 음성 설정·백그라운드 중지 검증

검증일: 2026-11-15. Pleos Connect v2.1.0 / emulator-5554 / com.firstride.qa39.

- 수정 전: 음성 설정을 끄고 소등 확인을 열었으나 SDK `doSpeak` 요청이 발생했다. 다른 앱(Settings)이 화면을 차지한 뒤에도 소등 구독 3개가 남았다.
- 수정 후 real 모드: 음성 꺼짐에서 실내등 OFF→ON 변경이 화면에 반영되고 TTS 요청은 0회였다. 백그라운드에서 구독은 0개, 복귀 후 3개와 새 OFF 상태를 확인했다.
- 음성 켜짐에서 TTS 요청을 확인했다. 재생 중 Settings로 가면 SDK stop과 구독 0개를 확인했다. 백그라운드에서 실내등을 ON으로 바꾸고 돌아오니 새 ON 요약만 읽었으며 이전 OFF 요약 요청은 없었다.
- D에서 구독 0개, P 복귀 후 현재 상태·구독 3개, 닫기 후 구독 0개를 확인했다.
- `gradlew.bat :app:testDebugUnitTest :app:assembleDebug` 통과(app 10개, 실패 0). 화면 배치·문구는 바꾸지 않았다. Android의 repeatOnLifecycle을 사용했으며 이 래퍼를 따라 쓰는 단위 테스트는 추가하지 않았다.
- 검증 후 원래 음성 설정(true), 에뮬레이터 실내등 OFF, QA fake 신호원을 복구했다.

전체 등이 미지원인 실제 차량·SDK 오류 경로는 이번 에뮬레이터에서 재현하지 않았다. 미지원·실패 판정은 기존 LightsCheckTest가 유지되며, 관찰이 끝나도 결과를 STOP까지 유지하도록 했다.


# W-55 소등 최초 미응답 검증

검증일: 2026-11-15.

- 신규 LightsObservationTest 6개: 지원 여부 동시 조회/순서/UNKNOWN 제외, 일부 미응답 후 늦은 ON 반영, 최초 응답 전 취소, 모든 등 미응답의 NONE_READABLE, 지원 조회 취소, 지원 등 없음. 정상 응답 뒤 가상 60초에도 실패로 바꾸지 않음을 함께 확인했다.
- 기존 동작을 engine 함수로 옮긴 직후 신규 첫 4개 중 3개가 실패했다. 공유 제한을 적용한 뒤 신규 6개와 기존 원상 복귀 회귀 테스트가 통과했다.
- 실제 Pleos Connect v2.1.0 / emulator-5554 / com.firstride.qa39 / real에서 W-54 소등 전체 경로를 재실행했다. OFF→ON 화면 갱신, 음성 꺼짐 TTS 요청 0회, 켜짐 재생, 백그라운드 stop/구독 0개, 복귀 시 새 요약만, D/닫기 구독 0개를 확인했다. 원래 음성 true·Park·실내등 OFF·QA fake로 복구했다.
- 첫 재실행은 홈 준비 전에 설정을 눌러 진입에 실패했다. 홈 확인 대기를 추가한 뒤 위 전체 경로가 통과했다.

실제 SDK 미응답/지연 장애는 단위 테스트로만 주입했다. 실제 차량과 전체 미지원 SDK는 실측하지 않았다.


# W-56 STT 미연결 가용성 표시 검증

검증일: 2026-11-15.

- 신규 VoiceCoordinatorTest 2개가 수정 전 실패했다. STT 미연결과 listenEnabled=false에서 초기 sttAvailable=false를 확인하며 TTS 안내는 정상 유지한다. 듣기 비활성 시 SDK 듣기 요청은 0회다.
- 수정 후 `gradlew.bat :engine:test :platform:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug` 통과(engine 182, platform 41, app 10개, 실패 0).
- Pleos Connect v2.1.0 / emulator-5554 / com.firstride.qa39에서 fake 차량 신호 + 실제 TTS SDK로 확인했다. 단계 화면에 음성 진행 꺼짐·터치 진행 문구가 표시되고, SDK doSpeak 안내 요청을 확인했다. 듣는 중 표시는 없고 터치 건너뛰기로 2단계에 도달한 뒤 그만하기로 홈 복귀했다.
- 캡처로 안내 문구와 단계·버튼이 함께 보이는 것을 확인했다. 화면 배치와 off 필드 의미를 유지해 동료 디자인의 MicButton 활성화 조건과 호환한다.

이 실행은 실제 차량 신호 판정·사람의 발화 검증을 대신하지 않는다. 앱의 stt=null은 유지한다.
