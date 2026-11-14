# 첫 탑승 조작 실습 (가칭)

처음 타는 차에서 출발 전 정차 상태로 순정 버튼을 직접 조작하면, 차량 신호를 읽어 맞게 조작됐는지 판정해 주는 Pleos Connect 차량용 앱입니다.

현대자동차그룹 차량용 앱 개발 공모전 출품작이며 현재 기획·설계 단계입니다. 앱 이름은 아직 정하지 않았습니다.

## 저장소 구조

문서와 앱 코드를 한 저장소에서 관리합니다.

| 경로 | 내용 | 상태 |
|---|---|---|
| `docs/` | 기획·설계 문서 | 초안 v0.1 |
| `engine/` | 판정 엔진, 코스 모델, 음성 조율 (순수 Kotlin) | 예정 |
| `platform/` | Pleos SDK · AAOS 어댑터 | 예정 |
| `app/` | 화면, 코스 정의, 위치 안내 자산 | 예정 |
| `apk/` | release APK | 예정 |

Gradle 루트는 저장소 루트입니다. 모듈 구조와 의존 방향은 [기술 설계](docs/06-tech-design.md) 3절에 있습니다.

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

## 개발 환경

- Pleos Connect Emulator v2.1.0 이상 (전용 GPU 필요)
- Android API 28 이상, Gradle 8 이상, Kotlin
- 사용 SDK: Vehicle SDK 2.3.2, TextToSpeech SDK 3.3.1, SpeechToText SDK 3.3.1

실행 방법, 테스트 환경, 에뮬레이터 버전과 화면 규격, APK 위치는 앱 코드가 들어오면 이 문서에 추가합니다.
