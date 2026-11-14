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
- **도구 회귀 테스트**: `python3 -m unittest discover -s tools/tests`. 가짜 adb만 사용한다. Windows에서는 `BASH_EXE='C:/Program Files/Git/bin/bash.exe'`를 환경 변수로 지정한다.

- **에뮬레이터는 Cold boot로 켠다.** 그래야 차량 신호가 동작한다.
- **VC는 브라우저 세션이 살아 있어야 값을 받는다.** `vc.sh`가 요청 전에 세션 신호(`POST /browser/session`)를 보낸다. 세션이 3초 넘게 쉬었다 다시 열리면 VC가 자기 값을 차량에 다시 내보내 값이 바뀔 수 있다(내기 순환이 외기로 돌아간 적이 있다). 여러 단계를 이어서 할 때는 `vc.sh keepalive`를 뒤에서 돌린다.
- **WSL에서는 `adb.exe`가 연 포트가 Windows 쪽에만 있다.** 그래서 `vc.sh`는 Windows의 `curl.exe`를 쓴다.
- **AAOS에서 앱은 사용자 10으로 돈다.** `run-as`에 `--user $(adb shell am get-current-user)`를 주지 않으면 다른 사용자의 폴더에 쓴다(`signals.sh`가 처리).
- **Pleos의 홈 키는 앱을 가리지 않는다.** 백그라운드 전환을 보려면 다른 앱(예: `adb shell am start -a android.settings.SETTINGS`)을 앱 창에 띄운다.
- **로그 태그(debug 빌드만)**: `FR-TRACE` 화면 전환과 단계 상태, `FR-SIGNALS` 신호 구독 수, `FR-MEASURE` 실측 화면. release 빌드는 남기지 않는다(POL-S-7).
- **실측 화면**: `adb shell am start -n com.firstride/.measure.MeasureActivity` (debug). `--ez tts true`면 TTS 확인, `--ez stt true`면 STT 확인.
- **시간 재기**: logcat 시각(에뮬레이터)과 PC 시각은 어긋나고 중간에 튄다. 지연은 `adb shell 'echo $EPOCHREALTIME'`로 에뮬레이터 시계 기준으로 잰다.
