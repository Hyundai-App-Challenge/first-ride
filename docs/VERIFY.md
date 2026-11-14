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


# W-57 시작 전 값 조회 실습 범위 검증

검증일: 2026-11-15.

- 신규 CourseRunnerTest 3개: 기준 조회 전 단계/구독 없음, 준비 취소의 모든 읽기 정리와 재개 시 미확인 기준 유지, 3초 준비 미응답 후 단계 시작. 기존 재개·완료 후 재시도 테스트 2개에 기준 보존/재취득 확인을 추가했다.
- 실행기에서 아직 기준을 읽지 않는 상태에서는 5개 조건이 실패했다. 구현 후에는 테스트의 단계 초기 조회를 준비 조회로 오인한 호출 수와 fixture 기본 풍량(3)을 잘못 가정한 두 기대값을 바로잡았다. 준비 조회와 단계 재조회는 모두 유지한다.
- `gradlew.bat :engine:test :platform:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug` 통과(engine 185, platform 41, app 10개, 실패 0). 내레이션 테스트도 기준 준비 이후에 단계를 읽는다.
- 실제 Pleos Connect v2.1.0 / emulator-5554 / com.firstride.qa39 / real: CRS-01에서 제상·AC OFF로 시작 → 제상 ON PARTIAL → 백그라운드에서 AC ON → 재개 ALREADY_AT_TARGET → 제상 OFF/ON으로 새 조작 후 완료. 완료 목록에 원래 OFF였던 제상과 AC가 모두 표시됐다.
- 제상·AC를 원래 OFF로 되돌리면 목록이 사라지고 홈 복귀 후 구독 active=0이었다. 검증 후 VC 시작 값과 QA fake 모드를 복구했다.

준비 중 SDK 미응답·취소는 단위 테스트로 주입했다. 실제 SDK 지연 장애와 실차는 미검증이다.


# W-58 위치 그림 전환 상태 검증

검증일: 2026-11-15. UI/UX의 상태 일치 원칙에 따라 그림 경로가 달라지면 이미지 상태를 새로 만든다. 자산과 배치는 유지한다.

- QA에만 loadImage 8초 지연을 임시로 넣고 실제 Pleos/VC의 CRS-01 WAITING→내기 순환 CORRECTION을 실행했다. 수정 전 새 그림 로딩 중 이미지 노드가 1개 남았고, 캡처에서도 제상 그림이 교정 문구 아래에 표시됐다.
- 수정 후 같은 지연에서 로딩 중 이미지 노드는 0개였다. 이전 제상 그림이 사라지고 문구는 유지됐다. 로딩 완료 후 내기 순환 강조 그림과 노드 1개를 확인했다.
- 임시 지연을 제거한 뒤 CourseTexts.kt diff가 없음을 확인했다. `gradlew.bat :app:testDebugUnitTest :app:assembleDebug` 통과(app 10개, 실패 0). Compose key 동작을 복제한 단위 테스트는 추가하지 않았다.
- 최종 APK를 다시 설치해 fake 차량 신호에서 CRS-01 정상 그림 표시와 CRS-04 누락 그림의 위치 문구 안내를 확인하고 그만하기로 홈 복귀했다. 실제 TTS SDK는 그대로 사용한다.

지연 검증 캡처는 로컬 captures/w58-red-during-load.png, w58-green-during-load.png, w58-green-loaded.png에 있다. 지연 probe와 캡처는 커밋하지 않는다. 실제 실내 자산 교체·기준 차종 확정은 별도다.


# W-59 CRS-01 공통 위치 문구 검증

검증일: 2026-11-15. 단계 공통 문구가 A/C·내기 순환 그림에서도 제상 전용 위치를 가리키는 문제를 코스 문구만으로 수정했다. 제상 모양 설명은 기본 instruction에 두고 공통 locatorText는 차량 공조 조작부로 안내한다. JSON 형식은 유지한다.

- `gradlew.bat :engine:test --rerun-tasks`로 코스 데이터 테스트를 실제 재실행해 185개 통과했다. `:app:testDebugUnitTest :app:assembleDebug`는 성공했고 app 테스트는 기존 결과를 재사용(UP-TO-DATE)했다. 문구 자체를 복제한 단위 테스트는 추가하지 않았다.
- 실제 Pleos Connect v2.1.0 / emulator-5554 / com.firstride.qa39 / real에서 WAITING은 제상 모양 안내, PARTIAL은 A/C 그림, CORRECTION은 내기 순환 그림을 확인했다. 후자의 두 상태에는 부채꼴 문구가 남지 않는다.
- 세 상태 모두 어디 있어요 버튼으로 공통 위치 문구의 SDK doSpeak 요청을 확인했다. 캡처에서 내기 순환 그림과 공조 조작부 문구가 일치한다. 검증 후 VC 시작 값·QA fake를 복구했다.

기준 차종 미정 상태에서 실차 좌표를 임의로 추가하지 않았다. 차종별 정확한 실내 배치·자산의 검증은 별도이며 동료 자산 PR은 수정하지 않았다.


# W-60 코스 자산 테스트 입력 검증

검증일: 2026-11-15. 실제 assets를 읽는 engine:test에 해당 폴더를 입력으로 선언하고 상대 경로로 정규화했다. 실행 코드는 바꾸지 않았다.

| 조건 | 수정 전 | 수정 후 |
|---|---|---|
| 같은 입력의 반복 `:engine:test` | UP-TO-DATE | UP-TO-DATE |
| CRS-01 차종 JSON에 유효한 공백 한 줄 추가 | UP-TO-DATE여서 새 데이터 미검증 | 테스트 실제 실행. UP-TO-DATE/FROM-CACHE가 아님을 조건 검사로 확인 |
| JSON을 원래 바이트로 복원 후 같은 입력 반복 | 원복 diff 없음 | 원복 diff 없음, 이후 반복 UP-TO-DATE |

- 모든 단계는 `gradlew.bat :engine:test --console=plain`을 사용했으며 새 입력 검증 스크립트가 성공했다. 엔진 185개, 실패 0.
- JSON 바이트는 같은 명령의 finally에서 복원했으며 동시 수정 여부도 확인했다. JSON git diff 없음.
- `gradlew.bat :app:assembleDebug` 성공. 빌드 입력 선언만 바꿔 화면 재검증이나 Gradle 자체를 복제한 단위 테스트는 추가하지 않았다. 새 의존성은 없다.


# W-61 코스 로더 Activity 캡처 검증

검증일: 2026-11-15. ViewModel에 보관하는 코스 로더가 Application 자산을 사용하도록 최소 수정했다. 화면 배치는 유지한다.

- javap 전후 확인: 코스 로더 Function0의 인수가 `MainActivity`에서 `FirstRideApp`으로 바뀌었다. ViewModel에 저장되는 로더의 Activity 직접 참조 경로가 없다.
- `gradlew.bat :app:testDebugUnitTest :app:assembleDebug` 성공(app 10개, 실패 0).
- 실제 Pleos/emulator-5554/com.firstride.qa39/fake에서 코스 로딩 후 와이퍼 2/2단계에 진입했다. font_scale 1.0→1.1로 Local Activity 인스턴스가 바뀌었고 프로세스 PID와 2/2단계는 유지됐다. 원래 1.0 복원 후에도 2/2를 확인하고 그만하기로 홈 복귀했다.
- 초기 UI 검사는 글자 크기 변경 후 포커스가 왼쪽 DrivingView로 옮겨 다른 창을 읽었다. 픽셀·창 포커스를 확인하고 기존 Activity를 재사용하는 reorder-to-front로 앱 창을 선택한 뒤 같은 조건을 통과했다.

실제 힙 덤프·GC 회수량은 측정하지 않았다. 검증은 직접 캡처 경로와 재생성 동작을 대상으로 한다. 새 의존성이나 글자 크기 설정 변경을 남기지 않았다.


# W-62 시작·홈 조회의 백그라운드 중지 검증

검증일: 2026-11-15. Pleos Connect v2.1.0 / emulator-5554 / com.firstride.qa39.

- 임시 debug 지원 조회 지연·대기 수 probe로 수정 전 시작 13개, 홈 3개가 Activity STOPPED 뒤에도 대기함을 재현했다. 신호 지원 결과는 fake이고 Activity·DrivingGate는 실제 에뮬레이터를 사용했다.
- 수정 후 시작 확인 도중 백그라운드에서 대기 0개, 숨은 화면의 실제 VC D→P 전환에도 새 조회 0회를 확인했다. 복귀 시 시작 확인이 재실행돼 홈에 도달했다.
- 홈 지원 확인 3개도 백그라운드에서 0개로 취소됐고, 복귀 시 미완료 조회를 다시 실행했다. 완료 후 다시 백그라운드·복귀하면 추가 조회가 없었다.
- 초기 시도는 앱 창 준비 전에 Settings를 열어 STOPPED에 도달하지 못했다. 창 포커스 대기를 추가했다. 이후 8초 probe 중 adb 재연결로 홈 확인을 놓쳐 20초로 늘린 실행에서 전체 조건이 통과했다.
- 임시 probe를 제거하고 Variant.kt diff가 없음을 확인했다. `gradlew.bat :engine:test :platform:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug` 성공. 보고서 합계 engine 185/platform 41/app 10개, 실패 0이며 변경 없는 작업에는 Gradle의 기존 결과를 재사용했다.
- 최종 APK의 real SDK에서 시작→CRS-01 WAITING→Settings→복귀 확인→이어 하기 WAITING→그만하기→홈을 통과했다. fake 복구 후 글자 크기 1.0→1.1로 Activity 인스턴스 변경·동일 PID·와이퍼 2/2 유지, 원래 1.0 복원 후 2/2 유지를 확인하고 홈으로 돌아왔다.

QA 신호원 fake·기어 P·글자 크기를 복구했다. 실제 SDK의 지원 조회 지연 장애와 실차는 주입·실측하지 않았다. 완료한 홈 조회는 같은 구성의 캐시이며 소등 확인 화면의 현재 상태 재조회는 그대로 유지한다.


# W-63 이미 목표 상태의 안내 검증

검증일: 2026-11-15. 켜기 전용 틀을 목표에 중립적인 재조작 안내와 해당 단계 instruction으로 바꿨다. 조건 행·정책의 설명도 맞추며 판정과 JSON 형식은 유지한다.

- StepViewTest에 OFF/LOW 목표 2개를 추가하고 기존 ON·문구 누락 조건을 갱신했다. 수정 전 13개 중 4개가 실패했다. 수정 뒤 전체 실행에서 기존 NarrationTest의 이전 문구 기대값 1개가 실패해 새 안내의 실제 발화 기대값으로 맞췄다.
- `gradlew.bat :engine:test :platform:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug` 성공(engine 187/platform 41/app 10개, 실패 0). OFF 테스트는 목표 상태에서 미완료, LOW 변경 후 WAITING, OFF 복귀 후 DONE을 함께 확인한다. 화면·음성은 같은 목표 안내를 쓴다.
- 실제 Pleos Connect v2.1.0/emulator-5554/com.firstride.qa39/real: 와이퍼 OFF로 시작하고 첫 단계를 건너뛰어 2단계 ALREADY_AT_TARGET에 진입했다. 중립 문구와 실제 OFF instruction, 다시 듣기의 동일 SDK doSpeak를 확인했다. LOW→OFF 조작으로만 완료 화면에 도달했다.
- 제상·AC ON/외기로 시작한 CRS-01에서도 ALREADY_AT_TARGET의 실제 instruction·조건 행·동일 SDK doSpeak를 확인했다. 제상 OFF→ON 뒤 완료됐다. 아무 조작 없이 자동 완료되지 않았다.
- 첫 UI 검사는 단일 조건인 와이퍼에도 조건 행이 있다고 가정해 실패했다. 기존 ConditionList는 조합 조건만 표시하므로 CRS-01에서 행을 검사하도록 고친 뒤 전체 SDK 경로가 통과했다. 캡처에서 OFF 안내·조작 버튼이 잘림 없이 보였다(captures/w63-off-instruction.png, 로컬).

검증 후 원래 와이퍼 OFF·공조 값·QA fake를 복구했다. 와이퍼 그림 누락은 동료 자산 PR 범위이며 수정하지 않았다. 실차의 조작부 위치·STT 발화 정확도는 미검증이다.


# W-64 포함된 코스와 배포 조회 권한 검증

검증일: 2026-11-15. CRS-01~06·소등 확인이 쓰는 차량 조회 권한을 main으로 옮기고 debug의 중복 선언을 제거했다(POL-S-10, 기술 설계10-1).

| 성공 기준 | 실행 확인 | 결과 |
|---|---|---|
| 포함된 코스의 release 권한 | processReleaseManifest 결과 XML 검사 | 수정 전 CAR_LIGHTS/WINDOWS/DOORS 3개 누락으로 assertion 실패. 수정 후 차량 조회 4종+TTS 정확히 일치 |
| 실측·STT debug 전용 | release/debug 병합 XML의 권한·Activity·package 비교 | STT_SERVICE와 MeasureActivity는 debug에만 있음. 제어·INTERNET·RECORD_AUDIO 권한 없음. release com.firstride/debug com.firstride.qa39 유지 |
| 배포 코드·QA 빌드 | gradlew.bat :app:processReleaseManifest :app:compileReleaseKotlin :app:testDebugUnitTest :app:assembleDebug | 성공. app 10개 실패0, 변경 없는 작업 결과 재사용. release 서명·설치는 실행하지 않음 |
| 기존 release 앱 보존 | adb dumpsys package의 설치·갱신 시각 전후 비교 | 동일. 삭제·설치·데이터 초기화 없음 |

같은 최종 QA APK/real SDK(Pleos Connect v2.1.0, emulator-5554)에서 추가 코스를 확인했다.

- CRS-02: 뒷유리 열선 OFF→ON, 풍량3→4 상대 변화, 풍향 Face→Defrost의 폴링을 거쳐 완료.
- CRS-03: Off→Position으로 미등, LowBeam으로 전조등, Position으로 전조등 끄기를 거쳐 완료.
- CRS-05: 반대쪽 BlinkRight의 CORRECTION, BlinkLeft로 첫 단계 완료, BlinkRight로 마지막 단계 완료.
- CRS-06: 운전석 창문0→10 상대 변화, 창문 잠금, 도어 잠금으로 완료.

처음 완료 로그 직후 UI를 눌러 아직 없는 노드를 읽어 화면 대기를 추가했다. 이후 방향지시등 완료 화면 검사 실패는 캡처·dumpsys로 사각지대 카메라(ai.umos.car.evs)의 포커스 점유임을 확인했다. 화면에는 코스 완료가 보였으나 UIAutomator에는 카메라 노드만 있었다. 방향지시등을 Off로 돌리고 앱을 다시 선택한 실행에서 CRS-05·06까지 통과했다. 중간 adb 재연결도 있었으며 제품 판정 실패와 구분했다.

원래 공조·전조등·방향지시등·창문·잠금 값과 QA fake 모드를 복구했다. 실제 release APK 설치·실차·전체 모델의 조작부 일치는 미검증이다. CRS-01/04는 직전 W-63 real SDK 결과가 있고 이번 권한 이동은 debug의 최종 권한 집합을 바꾸지 않는다.


# W-65 코스 조작·복귀 문구 정합성 검증

검증일: 2026-11-15. JSON 형식·목표 토큰·판정은 유지하고 코스 문구 3곳만 수정했다.

- [현대 공식 Lighting Control](https://ownersmanual.hyundai.com/full_webhelp/NE1a/2025/en_US/id4229c6bd123.html)은 AUTO가 주변 조도에 따라 전조등을 켜거나 끈다고 설명한다. AUTO를 OFF 보장 대안에서 제외하고 현재 VC/SDK에서 확인한 미등 위치를 안내한다. 사용자 특정 차종의 실내 배치를 확인했다는 뜻은 아니다.
- `gradlew.bat :engine:test :app:testDebugUnitTest :app:assembleDebug` 성공. 자산 변경으로 engine:test가 UP-TO-DATE가 아닌 실제 실행됐으며 187개 실패0. app 10개는 기존 결과 재사용. 단순 문구를 복제하는 새 테스트는 추가하지 않았다.
- 실제 Pleos/emulator-5554/com.firstride.qa39/real의 CRS-03에서 Off→Position→LowBeam으로 3단계에 도달했다. 새 미등 위치 안내와 다시 듣기의 동일 SDK doSpeak를 확인하고 Position으로 완료했다.
- 같은 APK의 CRS-05에서 Off→BlinkLeft→BlinkRight로 완료했다. 실제 오른쪽 점등·카메라가 보이는 캡처의 원상 복귀 목록은 이제 "방향지시등"이며 이전의 "왼쪽 방향지시등" 오표시가 없다(captures/w65-right-restore.png, 로컬 픽셀 확인).
- 방향지시등을 Off로 직접 되돌린 후 목록에서 사라지는 것을 UI 노드로 확인하고 홈으로 돌아왔다. 원래 등·방향지시등 값과 QA fake 모드를 복구했다.

좌우 목표·단계 안내·완료 문구는 그대로다. 실차 AUTO 조건·차종별 스위치 형상은 미검증이다.
