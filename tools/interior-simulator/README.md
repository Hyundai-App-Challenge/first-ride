# 아이오닉5 공조 사진 실습

앱의 CRS-01/02 안내와 같은 사진 위에서 조작하는 로컬 실습 화면이다. 웹 조작은 VC 차량 상태를 바꾸고, 앱은 기존 Pleos SDK·engine으로 판정한다. 웹에서 앱 완료를 강제로 만들지 않는다.

## 실행

1. Android Studio에서 기존 Pleos AVD를 Cold Boot하고 이 브랜치의 `app`을 실행한다. 기어는 P로 둔다.
2. debug 신호원을 REAL로 선택한다. 가짜 신호 모드에서는 웹 조작이 앱에 반영되지 않는다.
3. 저장소 루트에서 실행한다(Python 3, 추가 패키지 불필요). 서버 터미널은 열어 둔다.

```powershell
& D:/Android/Sdk/platform-tools/adb.exe devices
# 목록에서 확인한 기기 번호를 사용한다.
& D:/Android/Sdk/platform-tools/adb.exe -s <기기번호> forward tcp:8088 tcp:8088
python tools/interior_simulator.py
```

새 실내 화면: **http://127.0.0.1:8090/**. 공식 VC는 http://127.0.0.1:8088/ 에 그대로 있다. 에뮬레이터 재시작 후에는 포트 연결을 다시 한다. 포트는 `--port 8091 --vc-port 8088`로 지정할 수 있다. 종료는 Ctrl+C다.

현재 PC의 QA 앱 REAL 모드 설정은 다음과 같다. `APP_ID`는 설치한 APK의 ID와 맞춘다. `.qa39`는 이 PC의 기존 debug suffix이며 저장소 기본 설정을 바꾸지 않는다.

```powershell
$env:ADB = 'D:/Android/Sdk/platform-tools/adb.exe'
$env:ANDROID_SERIAL = '<기기번호>'
$env:APP_ID = 'com.firstride.qa39'
& 'C:/Program Files/Git/bin/bash.exe' tools/signals.sh real
```

## 직접 확인

- 앱에서 김서림 제거를 시작하고 사진의 앞유리 제상·A/C·내기/외기 버튼을 조작한다. 현재 상태에 따라 부분 충족·교정·완료가 달라진다.
- 뒷유리 열선·풍량·풍향에서는 열선을 켜고 풍량을 올린 다음 **바람 방향 → 앞유리**를 선택한다. 얼굴은 완료 조건이 아니다.
- 이미 목표 상태라면 앱 안내에 따라 껐다 켜는 등 재조작한다. 처음부터 켜 놓은 값은 새 조작으로 판정하지 않는다.
- 풍향 사진 버튼은 선택 영역으로 이동한다. 얼굴·발·앞유리는 **운전석**에 적용된다.
- 작은 화면에서는 사진을 좌우로 움직인다. `조작 버튼 전체 보기`와 키보드 탐색으로도 같은 조작을 할 수 있다.
- 연결이 끊기면 조작이 비활성화된다. 연결을 복구하면 현재 상태를 다시 읽는다. 값은 읽기 확인 후 표시하며, 실습 완료는 앱에서 확인한다.

## 공통 자산과 범위

기준 팩은 `app/src/main/assets/panels/ioniq5-2025/`다. `panel.json`의 사진·crop·정규화 버튼 좌표·표시 이름을 웹과 Android가 함께 읽는다. Android는 같은 crop 위에 현재 안내 버튼만 강조한다. 두 CRS JSON은 공조 안내만 덮어쓰고 다른 네 코스는 기존 reference 예시를 유지한다. 전체 차종 모델을 완성한 것으로 취급하지 않는다.

지원: 공조 켜기(AUTO 위치)/끄기(OFF), 앞·뒷유리 제상, A/C, 내기/외기, 풍량 0~8, 풍향 Face/Floor/Defrost, 운전석 온도 16~30℃. OFF는 반복해도 켜지지 않는다.

사진의 숫자·LED는 촬영 당시 모습이고 라이브 값은 상태판에 표시한다. AUTO 위치는 공조 전원을 켜는 동작만 재현한다. 실차의 자동 풍량·에어컨·순환 연동 전체, 동승석 조작, 최대 제상 전용 조작은 재현하지 않는다. 앱의 최대 제상 후속 단계도 이를 명시하고 건너뛸 수 있다. 사진 기반 교육용 모델이며 실차의 완전한 디지털 트윈이나 해당 차량의 Pleos 탑재를 보증하지 않는다.

VC 연결은 기존 `tools/vc.sh`와 같은 2.1.0 HTTP 경로(`/browser/session`, `/vehicle/state`, `/vehicle/hvac`)를 쓴다. 공개 안정 API로 확인된 계약은 아니므로 버전이 바뀌면 재검증한다. 웹은 1초마다 세션·상태를 확인한다. 다른 탭/도구와 동시에 값을 쓰지 않는다. 세션이 3초 이상 쉬면 재접속 시 VC가 상태를 재전송할 수 있다.

## 검증

```powershell
$env:BASH_EXE = 'C:/Program Files/Git/bin/bash.exe'
python -m unittest discover -s tools/tests
node --check tools/interior-simulator/app.js
python tools/check-interior-ui.py --chrome 'C:/Program Files/Google/Chrome/Application/chrome.exe'
python tools/check-interior-ui.py --chrome 'C:/Program Files/Google/Chrome/Application/chrome.exe' --mobile
```

UI 검증은 가짜 VC와 독립 headless Chrome 프로필을 사용하며 사용자 브라우저나 차량을 바꾸지 않는다. 모바일은 실제 390 CSS px 프레임에서 확인한다. 테스트 서버에서만 같은 출처 프레임을 허용하고 제품 서버의 frame 차단 정책은 유지한다. 화면 기록은 git에서 제외된 `captures/`에 저장한다.

## 사진과 조작 위치 출처

- [현대 공식 아이오닉5 기능 소개](https://www.hyundai.com/uk/en/models/ioniq5/features.html), 공개 사진 `Hyundai_IONIQ_5_Interior_2024_Dual_full_auto_air_conditioning`(부분변경 패널). 원본을 수정하지 않고 화면에서 crop·강조한다. 사진의 권리는 현대자동차에 있다.
- [2025 한국어 사용설명서 — 수동으로 조절하기](https://ownersmanual.hyundai.com/full_webhelp/NE1/2025/ko_KR/id042e6950a36.html). 기준 연식·트림은 사용자 테스트 후 확정한다.
