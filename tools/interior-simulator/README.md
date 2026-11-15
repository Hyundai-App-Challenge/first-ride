# 아이오닉5 운전석 사진 실습

앱의 CRS-01~06 안내와 같은 실내 사진·공식 설명서 그림 위에서 조작하는 로컬 실습 화면이다. 웹 조작은 VC 차량 상태를 바꾸고, 앱은 기존 Pleos SDK·engine으로 판정한다. 웹에서 앱 완료를 강제로 만들지 않는다.

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

- 운전석 전체 사진에서 왼쪽 레버·오른쪽 레버·공조·도어를 선택하면 해당 위치로 이동한다. 상단 위치 버튼과 뒤로 가기도 같은 탐색을 제공하며 차량 값을 바꾸지 않는다. `/#lights`, `/#climate`처럼 위치를 직접 열 수 있다.
- 전체 사진의 디스플레이에서 **홈·공조·차량**을 누르면 화면이 바뀐다. 이 화면은 추가한 교육용 UI이며 현대 순정 인포테인먼트의 완전한 복제가 아니다. 숫자·조명 모드는 VC에서 읽은 현재 값이다.
- 앱의 전조등·미등 코스는 조명 화면에서 **미등 → 전조등 → 미등** 순서로 조작한다. 왼쪽 레버 끝을 누르면 선택 영역으로 이동한다. OFF·AUTO도 선택할 수 있다. AUTO는 VC 모드만 설정하며 실제 조도·자동 점등까지 재현하지 않는다.
- 조명 그림의 긴 레버 부분이나 `방향지시 레버 전체 보기`를 누르면 같은 차종의 방향지시 전용 그림으로 이동한다. 위쪽 끝 A=오른쪽, 아래쪽 끝 A=왼쪽, 가운데=중립이며 앱 안내도 같은 그림·방향이다. B 원터치·조향 자동 취소는 제공하지 않는다.
- 사진 위 조명 끝 스위치를 **위아래로 굴린다**. 위로는 OFF → AUTO → 미등 → 전조등, 아래로는 반대다. 원통 축과 다른 사진 평면의 원형 회전 계산은 제거했다. 사진에 인쇄된 네 표시도 직접 누를 수 있다. 노란 선택 테두리는 각 표시의 원본 좌표에 맞춰 이동한다. 와이퍼도 OFF → AUTO → LO → HI의 실제 레버 위치를 따라 위아래로 끌거나 해당 위치를 누른다. 사진의 MIST·와셔액·감도 조절은 제공하지 않는다.
- 드래그는 **놓았을 때 한 번 쓰고**, 파란 점선은 놓기 전 미리보기다. 터치 취소·화면 이동은 쓰지 않는다. 방향키·Home/End, 기존 선택 버튼도 같은 값으로 조작한다. 입력 후 확인한 상태를 표시하며 실제 실습 판정은 앱이 한다.
- 와이퍼 동작 예시는 LO 2.2초/HI 1초 왕복으로 구분한다. 이는 실제 주기 실측값이 아니다. 방향지시등은 확인된 쪽만 점멸한다. OS의 모션 줄임 설정에서는 점멸·왕복 대신 정적인 동작 상태를 표시한다. AUTO의 강우·조도 판단과 조향에 따른 방향지시등 자동 취소는 연결하지 않는다.
- 창문·잠금에서는 운전석 창문을 10%씩 열고 닫으며 뒷좌석 창문 잠금을 전환한다. 0%는 완전히 닫힘, 100%는 완전히 열림이다. `도어 잠금 버튼 확대`로 이동하면 잠금·해제 버튼을 각각 누를 수 있다. 창문 잠금은 VC의 area=all을 사용하며 전자식 차일드 락 전체를 재현하지 않는다.
- 창문 버튼은 누르고 있으면 확인된 값에서 10%씩 약 300ms 간격으로 계속 움직인다. 놓기·포인터 취소·화면 이동·포커스 상실·연결 실패에서 반복을 멈춘다. 이미 보낸 쓰기 한 건은 완료될 수 있다. 실제 모터 속도나 원터치 자동 열림 기능을 재현한 것은 아니다.
- 앱에서 김서림 제거를 시작하고 사진의 앞유리 제상·A/C·내기/외기 버튼을 조작한다. 현재 상태에 따라 부분 충족·교정·완료가 달라진다.
- 뒷유리 열선·풍량·풍향에서는 열선을 켜고 풍량을 올린 다음 **바람 방향 → 앞유리**를 선택한다. 얼굴은 완료 조건이 아니다.
- 이미 목표 상태라면 앱 안내에 따라 껐다 켜는 등 재조작한다. 처음부터 켜 놓은 값은 새 조작으로 판정하지 않는다.
- 풍향 사진 버튼은 선택 영역으로 이동한다. 얼굴·발·앞유리는 **운전석**에 적용된다.
- 작은 화면에서는 사진을 좌우로 움직인다. 사진 옆(모바일에서는 아래)의 조작 버튼과 키보드 탐색으로도 같은 조작을 할 수 있다.
- 연결이 끊기면 조작이 비활성화된다. 연결을 복구하면 현재 상태를 다시 읽는다. 값은 읽기 확인 후 표시하며, 실습 완료는 앱에서 확인한다.

## 공통 자산과 범위

기준 팩은 `app/src/main/assets/panels/ioniq5-2025/`다. `panel.json`의 views별 사진·crop·정규화 버튼 좌표·표시 이름을 웹과 Android가 함께 읽는다. Android는 해당 장면의 같은 crop 위에 현재 안내 버튼만 강조한다. CRS-01~06 JSON으로 실내 조작 안내를 덮어쓰며 기존 reference는 나머지 자산의 fallback이다. 부분 사진 팩은 기존 panels 경로에 두며 models/ 전체 모델 계약을 바꾸지 않는다.

지원: 공조 켜기(AUTO 위치)/끄기(OFF), 앞·뒷유리 제상, A/C, 내기/외기, 풍량 0~8, 풍향 Face/Floor/Defrost, 운전석 온도 16~30℃. OFF는 반복해도 켜지지 않는다.

사진의 숫자·LED는 촬영 당시 모습이고 라이브 값은 상태판과 추가한 디스플레이에 표시한다. 레버·잠금 확대는 같은 차종의 공식 설명서 그림이다. 공조 AUTO 위치는 공조 전원을 켜는 동작만 재현한다. 실차의 자동 풍량·에어컨·순환 연동 전체, 동승석 조작, 최대 제상 전용 조작은 재현하지 않는다. 앱의 최대 제상 후속 단계도 이를 명시하고 건너뛸 수 있다. 사진 기반 교육용 모델이며 실차의 완전한 디지털 트윈이나 해당 차량의 Pleos 탑재를 보증하지 않는다.

VC 연결은 기존 `tools/vc.sh`와 같은 2.1.0 HTTP 경로(`/browser/session`, `/vehicle/state`, `/vehicle/hvac`, `/vehicle/lights`, `/vehicle/turn-signal`, `/vehicle/body`, `/vehicle/window-lock`)를 쓴다. 공개 안정 API로 확인된 계약은 아니므로 버전이 바뀌면 재검증한다. 웹은 1초마다 세션·상태를 확인한다. 다른 탭/도구와 동시에 값을 쓰지 않는다. 세션이 3초 이상 쉬면 재접속 시 VC가 상태를 재전송할 수 있다.

## 검증

```powershell
$env:BASH_EXE = 'C:/Program Files/Git/bin/bash.exe'
python -m unittest discover -s tools/tests
node --check tools/interior-simulator/app.js
python tools/check-interior-ui.py --chrome 'C:/Program Files/Google/Chrome/Application/chrome.exe'
python tools/check-interior-ui.py --chrome 'C:/Program Files/Google/Chrome/Application/chrome.exe' --mobile
python tools/check-interior-ui.py --chrome 'C:/Program Files/Google/Chrome/Application/chrome.exe' --gestures
python tools/check-interior-ui.py --chrome 'C:/Program Files/Google/Chrome/Application/chrome.exe' --gestures --mobile
```

UI 검증은 가짜 VC와 독립 headless Chrome 프로필을 사용하며 사용자 브라우저나 차량을 바꾸지 않는다. 모바일은 실제 390 CSS px 프레임에서 확인한다. 테스트 서버에서만 같은 출처 프레임을 허용하고 제품 서버의 frame 차단 정책은 유지한다. 화면 기록은 git에서 제외된 `captures/`에 저장한다.

`--gestures`는 Node 22+의 기본 WebSocket과 Chrome 입력 프로토콜로 실제 마우스·터치·키보드 이벤트를 보낸다(추가 패키지 없음). 390px 기기 에뮬레이션, 좌우 어느 곳에서 시작해도 같은 상하 굴림, 가로 이동 쓰기0, 사진의 네 표시 직접 누르기, 조명·와이퍼·방향지시 마커의 원본 좌표(2 CSS px 이내), 터치 취소, LO/HI 왕복 속도, 방향지시등, 창문 놓기/이동/포커스 상실 정지를 검사한다. 제품 화면의 조작 이벤트를 우회해 값을 직접 바꾸지 않는다.

## 사진과 조작 위치 출처

- [현대 공식 아이오닉5 기능 소개](https://www.hyundai.com/uk/en/models/ioniq5/features.html), 공개 사진 `Hyundai_IONIQ_5_Interior_2024_Dual_full_auto_air_conditioning`(부분변경 패널). 원본을 수정하지 않고 화면에서 crop·강조한다. 사진의 권리는 현대자동차에 있다.
- [2025 한국어 사용설명서 — 수동으로 조절하기](https://ownersmanual.hyundai.com/full_webhelp/NE1/2025/ko_KR/id042e6950a36.html). 기준 연식·트림은 사용자 테스트 후 확정한다.
- 전체 운전석 사진: 같은 공식 기능 소개의 `Hyundai_IONIQ_5_2024_Interior_6-Enhanced-IONIQ-5-ccNC_edit`(2000×1600). 도어 사진: `Hyundai_IONIQ_5_Interior_2024_Electronic_Child_Lock`(1600×1067). 원본 JPEG 바이트를 보존하고 화면에서 확대·강조·교육용 화면을 겹친다.
- [실외 조명 스위치](https://ownersmanual.hyundai.com/full_webhelp/NE1/2025/ko_KR/id23C69600V5Z.html)의 `2C_LightSwitchOverview.jpg.png`, [앞유리 와이퍼](https://ownersmanual.hyundai.com/full_webhelp/NE1/2025/ko_KR/id23C6B060A4Y.html)의 `2C_WiperSwitchOverview.jpg.png`, [도어 잠금](https://ownersmanual.hyundai.com/full_webhelp/NE1/2025/ko_KR/id23C5F0V04ZJ.html)의 `2C_CentralDoorLockButton.jpg.png`(각 685×437). 원본 그림과 현대자동차의 권리 표시는 유지한다.
- [방향지시등 사용하기](https://ownersmanual.hyundai.com/full_webhelp/NE1/2025/ko_KR/id23C697005HS.html)의 `2C_TurnSignalSwitch.jpg.png`(685×437). 조명 조작 그림을 재사용하지 않고 A/B 상하 위치가 나타나는 전용 그림을 공유한다.
