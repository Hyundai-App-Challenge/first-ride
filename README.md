# FirstRide — 첫 탑승 조작 실습

처음 타는 차에서, 출발 전 정차한 채 순정 버튼을 직접 눌러 보며 익히는 Pleos Connect 차량용 앱입니다. 앱이 차량 신호를 읽어 맞게 조작했는지 바로 알려 줍니다.

- 팀: 처음타니
- 현대자동차그룹 차량용 앱 개발 공모전 출품작
- 앱 화면에 보이는 이름: 첫 탑승 조작 실습 (패키지 `com.firstride`, 버전 0.1.0)

## 무엇을 하나

1. 홈에서 익힐 코스를 고릅니다.
2. 단계마다 "어떤 버튼을 어떻게 누르세요"를 화면 문구, 실내 사진 위치 안내, 음성으로 알려 줍니다.
3. 사용자가 실제 버튼을 누르면 앱이 차량 신호로 확인해 다음 단계로 넘어갑니다. 앱은 차량을 제어하지 않고 읽기만 합니다.

| 코스 | 익히는 조작 |
|---|---|
| 김서림 제거 (대표 코스) | 앞유리 제상, 에어컨, 외기 유입 |
| 뒷유리 열선·풍량·풍향 | 뒷유리 열선, 풍량, 바람 방향 |
| 전조등·미등 | 조명 스위치 |
| 와이퍼 | 앞유리 와이퍼 레버 |
| 방향지시등 | 방향지시 레버 |
| 창문·도어 잠금 | 운전석 창문, 뒷좌석 창문 잠금, 도어 잠금 |

내리기 전에는 "소등 확인"으로 켜 둔 등을 점검합니다. 주행 중에는 실습을 막고(기어 P에서만 진행), 기록은 기기 안에만 저장합니다.

## APK

- 위치: [`apk/처음타니_FirstRide_release.apk`](apk/)
- 실행 가능한 release 빌드입니다(서명됨, versionCode 1 / versionName 0.1.0).

## 테스트 환경

| 항목 | 값 |
|---|---|
| 에뮬레이터 | **Pleos Connect Emulator v2.1.0** (AVD `Pleos_Connect_v2.1.0`, x86_64, 전용 GPU 필요) |
| 화면 규격 | **2560×1440 px, 160dpi** (1dp = 1px) |
| 앱 창 | 가로형 **1682×1210 dp**(기본), 내비게이션과 나눠 쓸 때 세로형 **841×1210 dp** |
| Android | API 28 이상 (target 34) |
| 사용 SDK | Pleos Vehicle SDK 2.3.2, TextToSpeech SDK 3.3.1, SpeechToText SDK 3.3.1 |
| 차량 값 | 에뮬레이터에 들어 있는 Virtual Console(VC)로 바꿉니다 |

퍼미션은 차량 조회(`CAR_CLIMATE`, `CAR_LIGHTS`, `CAR_WINDOWS`, `CAR_DOORS`)와 음성(`TTS_SERVICE`, `STT_SERVICE`)만 씁니다. 차량 제어 퍼미션과 인터넷 퍼미션은 없습니다.

## 실행 방법

### 1. 에뮬레이터 준비

1. Android Studio의 Device Manager에서 Pleos Connect v2.1.0 AVD를 **Cold Boot**로 켭니다. 일반 부팅이나 스냅샷에서는 차량 신호가 들어오지 않을 수 있습니다.
2. VC에서 기어를 **P**로 둡니다. P가 아니면 앱은 "차를 세운 상태에서 진행하세요" 화면만 보여 줍니다.

### 2. APK 설치와 실행

```bash
adb devices
adb install -r apk/처음타니_FirstRide_release.apk
adb shell am start --user $(adb shell am get-current-user) -n com.firstride/.MainActivity
```

Pleos Connect에서 앱은 Android 사용자 10으로 돕니다. 앱 목록의 "첫 탑승 조작 실습"을 눌러도 됩니다. 저장소의 `tools/install.sh release`는 사용자 확인과 설치·실행을 한 번에 합니다.

### 3. 코스 해 보기

1. 홈에서 "김서림 제거"를 고르고 "시작하기"를 누릅니다.
2. 화면 안내대로 VC에서 해당 값을 바꿉니다(예: 앞유리 제상 켜기). 앱이 신호를 확인하면 단계가 넘어갑니다.
3. 마지막 단계까지 가면 완료 화면과 원상 복귀 안내가 나옵니다.

VC 대신 실제 차 실내 사진 위에서 버튼을 눌러 보고 싶다면 [실내 사진 실습 화면](tools/interior-simulator/README.md)을 쓰세요. 웹에서 누른 값이 VC를 거쳐 앱에 그대로 들어갑니다.

### 음성

- 단계 안내는 TTS로 읽습니다.
- 음성 진행: 안내가 끝나면 "다음", "다시", "어디야" 세 단어를 듣습니다. 터치 버튼(다시 듣기, 어디 있어요, 건너뛰기)이 늘 같은 일을 합니다.
- 에뮬레이터에서 PC 마이크를 쓰려면 `emulator -avd Pleos_Connect_v2.1.0 -no-snapshot-load -allow-host-audio`로 켭니다. 에뮬레이터는 켜질 때의 기본 녹음 장치를 씁니다.
- 실제 발화의 어휘 인식은 아직 검증 중입니다([VERIFY.md](docs/VERIFY.md)).

## 빌드

```bash
./gradlew :app:assembleRelease
```

- JDK 17, Gradle 8.10.2(wrapper 포함), AGP 8.8.2, Kotlin 2.0.0
- release 빌드는 `apk/처음타니_FirstRide_release.apk`로 복사됩니다. 서명 키는 저장소 밖에 둡니다.
- 단위 테스트: `./gradlew :engine:test :platform:testDebugUnitTest :app:testDebugUnitTest`
- 도구 테스트: `python3 -m unittest discover -s tools/tests`

## 저장소 구조

| 경로 | 내용 |
|---|---|
| `app/` | 화면(Compose), 코스 정의, 실내 사진·위치 안내 자산 |
| `engine/` | 판정 엔진, 코스 모델, 음성 조율 (순수 Kotlin) |
| `platform/` | Pleos SDK · AAOS 어댑터 (차량 신호, TTS·STT, 주행 제한, 저장소) |
| `apk/` | 제출용 release APK |
| `tools/` | 에뮬레이터·VC 자동화 스크립트, 실내 사진 실습 화면 |
| `docs/` | 기획·설계 문서 |

## 문서

| 문서 | 답하는 질문 |
|---|---|
| [PRD](docs/01-prd.md) | 무엇을, 왜 만드는가. 무엇을 하지 않는가 |
| [용어집](docs/02-glossary.md) | 같은 대상을 무엇이라고 부르는가 |
| [기능 명세서](docs/03-feature-spec.md) | 정확히 어떻게 동작하는가. 언제 완료인가 |
| [사용자 플로우](docs/04-user-flow.md) | 화면이 어떤 순서로 이어지는가 |
| [정책서](docs/05-policy.md) | 이런 상황에서는 어떻게 처리하는가 |
| [기술 설계](docs/06-tech-design.md) | 어떤 구조로 만드는가 |
| [개발 계획](docs/07-dev-plan.md) | 누가 무엇을 언제까지 만드는가 |
| [DESIGN.md](DESIGN.md) | 화면의 색을 어떤 규칙으로 쓰는가 |
| [디자인 값](docs/08-design-tokens.md) | 글자·간격·크기·컴포넌트·배치의 값은 무엇인가 |
| [검증 기록](docs/VERIFY.md) | 무엇을 어떤 환경에서 확인했는가 |
