# tools/

에뮬레이터(Pleos Connect v2.1.0)와 Virtual Console(VC)을 사람 손 없이 다루는 스크립트. Git Bash, WSL과 리눅스·맥의 bash에서 쓴다.

| 스크립트 | 하는 일 |
|---|---|
| `adb.sh` | adb를 찾아 실행한다. `ADB` 환경 변수 → PATH의 `adb` → WSL에서 Windows의 `adb.exe` 순서 |
| `install.sh [debug\|release]` | APK를 설치하고 앱을 띄운다. 설치 실패 시 기존 앱을 삭제하지 않고 멈춘다. `ACTIVITY=.measure.MeasureActivity`로 띄울 화면을 바꾼다 |
| `vc.sh set <영역> k=v ...` | VC의 HTTP 요청으로 차량 값을 바꾼다. 예: `vc.sh set hvac ac=true recirc=false`, `vc.sh set gear gear=Drive`, `vc.sh set lights feature=headlamp_mode value=LowBeam`, `vc.sh set body front_wiper=Low` |
| `vc.sh state [영역]` | VC가 아는 차량 상태(JSON) |
| `vc.sh keepalive <초>` | VC 세션을 살려 둔다. 실측·녹화 중에 뒤에서 돌린다 |
| `signals.sh real\|fake` | debug 빌드의 신호원을 차량 SDK / 가짜 신호원으로 바꾸고 앱을 다시 띄운다 |
| `tap.sh "글자"` | 화면에서 글자가 같은 요소를 찾아 누른다(uiautomator) |
| `check-crs01.sh` | CRS-01 수용 기준(AC-CRS01-1·2·2b·2c·3·4)을 VC로 차례로 확인한다. debug + `signals.sh real` |
| `check-courses.py` | 설치된 debug 앱의 CRS-01~06 통합 회귀. 후속 조치·재개·소등·원상 복귀도 선택 검증하며 원래 VC 값·신호원 모드를 복원한다. Python 표준 라이브러리만 사용 |
| `demo-scene.sh reset\|<장면>` | 2차 PT 시연 장면(사용자 플로우 8절)의 VC 조작과 기대하는 앱 상태 |

## 알아 둘 것

- **VC 통신 실패**: 세션 연결·상태 조회·값 변경은 HTTP 4xx/5xx나 연결 오류 때 비정상 종료한다. `check-crs01.sh`는 신호 주입 실패 시 이전 값을 판정하지 않고 즉시 멈춘다.

- **대상 앱**: `install.sh`, `signals.sh`, `check-crs01.sh`는 `APP_ID`로 대상을 지정한다(기본 `com.firstride`). APK의 applicationId와 같은 값을 쓴다. 패키지에 suffix가 붙어도 Activity의 코드 네임스페이스는 `com.firstride`다. `ACTIVITY`는 전체 이름이나 `.measure.MeasureActivity`를 받는다.
- **기기 선택**: 먼저 `adb devices`로 확인하고 `ANDROID_SERIAL`을 지정한다. `ADB`는 실행 파일 경로이며 인수를 넣지 않는다.
- **Git Bash 예시**(기기 번호는 `adb devices`로 확인한 값):
  ```bash
  export ADB='D:/Android/Sdk/platform-tools/adb.exe'
  export ANDROID_SERIAL='<확인한 기기 번호>'
  export APP_ID=com.firstride.qa39
  tools/install.sh debug
  tools/signals.sh real
  tools/check-crs01.sh
  ```
- **도구 회귀 테스트**: `python3 -m unittest discover -s tools/tests`. 가짜 adb와 로컬 HTTP 서버를 사용하며 차량에는 연결하지 않는다. Windows에서는 `BASH_EXE='C:/Program Files/Git/bin/bash.exe'`를 환경 변수로 지정한다.

- **에뮬레이터는 Cold boot로 켠다.** 그래야 차량 신호가 동작한다.
- **VC는 브라우저 세션이 살아 있어야 값을 받는다.** `vc.sh`가 요청 전에 세션 신호(`POST /browser/session`)를 보낸다. 세션이 3초 넘게 쉬었다 다시 열리면 VC가 자기 값을 차량에 다시 내보내 값이 바뀔 수 있다(내기 순환이 외기로 돌아간 적이 있다). 여러 단계를 이어서 할 때는 `vc.sh keepalive`를 뒤에서 돌린다.
- **WSL에서는 `adb.exe`가 연 포트가 Windows 쪽에만 있다.** 그래서 `vc.sh`는 Windows의 `curl.exe`를 쓴다.
- **AAOS에서 앱은 사용자 10으로 돈다.** `run-as`에 `--user $(adb shell am get-current-user)`를 주지 않으면 다른 사용자의 폴더에 쓴다(`signals.sh`가 처리).
- **Pleos의 홈 키는 앱을 가리지 않는다.** 백그라운드 전환을 보려면 다른 앱(예: `adb shell am start -a android.settings.SETTINGS`)을 앱 창에 띄운다.
- **로그 태그(debug 빌드만)**: `FR-TRACE` 화면 전환과 단계 상태, `FR-SIGNALS` 신호 구독 수, `FR-MEASURE` 실측 화면. release 빌드는 남기지 않는다(POL-S-7).
- **내비게이션과 나눠 쓰는 화면 흉내(debug)**: `adb shell am start -n com.firstride/.MainActivity --ez nav_mock true`. 가로형 앱 창의 왼쪽 반에 내비게이션 mock을, 오른쪽 반(841dp)에 앱의 세로형 화면을 그린다(Figma 05 보드). 가짜 신호원일 때는 개발자 패널의 "내비 mock 켜기"로도 켠다. release에는 없다.
- **실측 화면**: `adb shell am start -n com.firstride/.measure.MeasureActivity` (debug). `--ez tts true`면 TTS 정상·중지·취소·100ms 간격 교체 후 마지막 완료를 확인한다. `--ez stt true`면 STT 확인.
- **시간 재기**: logcat 시각(에뮬레이터)과 PC 시각은 어긋나고 중간에 튄다. 지연은 `adb shell 'echo $EPOCHREALTIME'`로 에뮬레이터 시계 기준으로 잰다.
- **단계 빨리 넘기기(debug, 가짜 신호원)**: 단계 화면 오른쪽 아래의 "지금 단계 조작"을 누르면 그 단계의 목표 조작을 가짜 신호원에 대신 넣는다. 신호 이름을 몰라도 되고 VC도 필요 없다. `tools/tap.sh "지금 단계 조작"`으로도 누를 수 있다. 값만 바꾸고 판정은 신호로 하므로 완료 화면까지 평소와 같은 길을 지난다. 차량 SDK 신호(`signals.sh real`)에서는 보이지 않고 release에는 없다.

## 여섯 코스 통합 검증

먼저 debug APK를 빌드·설치하고 `adb devices`로 기기를 확인한다. `check-courses.py`는 앱을 설치하거나 데이터를 지우지 않는다. `APP_ID`와 `ADB`를 지정하며, 연결 기기가 여럿이면 `ANDROID_SERIAL`도 필수다. `run-as`가 허용되지 않는 release 앱은 VC 변경 전에 중단한다. `ACTIVITY` 환경 변수와 관계없이 앱의 MainActivity를 선택한다. 실행·중지는 확인한 Android 사용자에 한정하고, 판정 로그도 그 사용자의 대상 앱 프로세스만 읽는다.

```powershell
$env:ADB = 'D:/Android/Sdk/platform-tools/adb.exe'
$env:ANDROID_SERIAL = '<adb devices로 확인한 번호>'
$env:APP_ID = 'com.firstride.qa39'
python tools/check-courses.py
# 실패한 코스만 다시 확인:
python tools/check-courses.py --course CRS-05 --course CRS-06
# 기본 조합 복구·후속 조치·중단까지 함께 확인:
python tools/check-courses.py --follow-ups
# CRS-01과 후속 조치만 확인:
python tools/check-courses.py --course CRS-01 --follow-ups
# 재개·소등·원상 복귀까지 모두 확인:
python tools/check-courses.py --follow-ups --recovery
# 재개·소등·원상 복귀 묶음만 재검증:
python tools/check-courses.py --course CRS-02 --recovery
```

기어 P에서 실행한다. 코스는 실제 SDK 신호로 판정하며 debug 진행 기록이 완료로 바뀔 수 있다. 앱·설정 데이터를 삭제하지 않고, 종료·실패·Ctrl+C에서 원래 공조·등·와이퍼·창문·잠금 값과 real/fake 표식을 복원한다. VC의 all 제어로 복원할 수 없는 혼합 잠금 상태에서는 변경 전에 중단한다. 복원 실패도 비정상 종료하며 다른 도메인과 신호원 복원은 계속 시도한다.

CRS-01은 부분 충족·교정·이미 목표, CRS-02는 상대 풍량·폴링 풍향, CRS-03은 등 켜기/끄기, CRS-04는 HIGH/AUTO 교정, CRS-05는 반대 방향 교정, CRS-06은 상대 창문·창문/도어 잠금을 확인한다. 방향지시등의 사각지대 카메라 포커스는 Off 복귀 후 앱 UI를 다시 선택해 처리한다. 모든 선택 코스와 복원이 끝나야 최종 성공 문구·종료 코드 0이 나온다. 상세 CRS-01 수용 기준은 기존 `check-crs01.sh`도 유지한다.

`--follow-ups`는 CRS-01을 선택한 경우에만 쓴다. 에어컨 복구 중 제상 유지 조건이 바뀌면 완료하지 않는지, 실제 지원 최댓값의 풍량·온도 조치, 해결/미해결 응답, 이미 최대인 조치 제외, 백그라운드·D→P 중단 후 새 점검을 확인한다. 후속 조치 전후 저장된 진행 파일의 SHA-256이 같아야 통과한다. 이 옵션은 운전석 온도·기어도 원래 값으로 복원한다. VC 2.1.0에는 최대 제상 쓰기 항목이 없어 해당 조치의 완료 검증은 `SKIP`을 출력하고 건너뛰기 경로를 확인한다. `ALL SELECTED COURSES PASSED`가 이 미검증 항목까지 보장하지는 않는다.

`--recovery`는 CRS-02 선택이 필요하다. 정상 코스 검증 뒤 다단계 실습의 백그라운드·D→P 중단, 사용자 선택 뒤 현재 단계 재판정, 이미 목표 상태에서 재조작, 최초 원상 복귀 기준, 건너뜀 유지·중단 후 홈 선택을 확인한다. 완료 화면과 소등 확인도 숨었을 때 구독0, 복귀 후 최신 값, 직접 원복할 때 목록 갱신을 검증한다. 기존 완료 기록을 건너뜀 재시도로 낮추지 않는지와 소등 확인의 기록 불변을 SHA-256으로 확인한다. 종료·실패 시 실내등도 원래 값으로 복원한다. 음성 설정과 학습 기록은 초기화하지 않는다.

이 검증은 Pleos Connect v2.1.0의 VC 필드와 reference 코스를 기준으로 한다. 실차·다른 모델의 조작부 배치·사람의 STT 정확도를 대신하지 않는다. WSL의 Python은 Windows adb 포트에 직접 닿지 않을 수 있으므로 이 도구는 Windows Python 또는 같은 네트워크의 Linux/macOS Python으로 실행한다.
