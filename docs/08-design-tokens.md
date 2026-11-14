# 디자인 값 — 첫 탑승 조작 실습 (가칭)

- 문서 상태: 초안 v0.1 (2026-11-14)
- 관련 문서: [사용자 플로우](04-user-flow.md) · [정책서](05-policy.md) 10·11절 · [기술 설계](06-tech-design.md) 12절, 13-3절(G1·T14·T15) · [용어집](02-glossary.md)
- Figma: [와이어프레임 v0.1](https://www.figma.com/design/7nCbKAdriwwQWVaXXhEIyV/?node-id=0-1) — 이 문서의 값은 그 파일의 변수(Variables)·텍스트 스타일·컴포넌트와 같다. 값을 바꿀 때는 두 곳을 함께 고친다.
- 이 문서가 사용자 플로우 9절이 가리키던 `reference/design-guide.md`(만들어지지 않음)를 대신한다.
- 쓰는 사람: `Theme.kt`와 공통 컴포넌트(`Components.kt` 등)를 바꾸는 사람. 이 문서만 보고 Compose 코드를 쓸 수 있게 적었다.

## 0. 전제

| 항목 | 값 | 근거 |
|---|---|---|
| 화면 | 2560×1440 px, 160dpi (density 1.0) → **1dp = 1px**. Figma의 px 값이 그대로 dp·sp다 | 실측 G1 |
| 가로형 앱 창 (기본) | **1682×1210 dp** | 실측 G1 |
| 세로형 앱 창 (내비게이션과 나눠 쓸 때) | **841×1210 dp** | 실측 T14 |
| 주간·야간 | 시스템을 따른다(`isSystemInDarkTheme()`). 앱에 테마 설정이 없다 | POL-U-3, 실측 T15 |
| 움직임 | 진행 표시(스피너)와 누름 효과(95% 축소)뿐 | POL-U-5, 기술 설계 12절 |
| 상태 표시 | 색 + 기호 + 문구. 색만으로 구분하지 않는다 | POL-U-4 |

앱은 오른쪽 앱 창 안만 그린다. 창 바깥(왼쪽 차량 화면, 위 상태 표시줄, 아래 공조 바)은 플랫폼 영역이다.

## 1. 색

Figma 컬렉션 `color`, 모드 `Light`·`Dark`. 대비는 아래 표의 글자/바탕 조합을 모두 계산해 확인했다(본문 4.5:1 이상, 테두리·기호 3:1 이상).

### 1-1. 기본 색

| 이름 (Figma 변수) | Light | Dark | 쓰는 곳 | Material 3 `ColorScheme` |
|---|---|---|---|---|
| `color/bg/app` | `#F1F3F6` | `#121417` | 화면 바탕 | `background` |
| `color/bg/surface` | `#FFFFFF` | `#1C1F24` | TopBar, CourseCard, SecondaryButton 바탕 | `surface`, **`surfaceContainerHighest`·`surfaceContainer`·`surfaceContainerLow`도 같은 값** (M3 `Card`의 기본 바탕이 `surfaceContainerHighest`라서) |
| `color/bg/surface-variant` | `#E8EBF0` | `#262A31` | VoiceBar, 안내 상자(정차 안내 등) | `surfaceVariant` |
| `color/bg/blocked` | `#E3E6EB` | `#2A2E35` | SCR-08 주행 중 차단 화면 전체 | (앱 색, `AppColors.blockedBg`) |
| `color/text/primary` | `#16181C` | `#F1F3F6` | 본문, 제목 | `onSurface`, `onBackground` |
| `color/text/secondary` | `#4A505A` | `#B4BAC4` | 보조 문구(예상 시간, 설명, 위치 문구), 단계 위치 1/3 | `onSurfaceVariant` |
| `color/text/on-primary` | `#FFFFFF` | `#0A1F52` | PrimaryButton 글자 | `onPrimary` |
| `color/text/disabled` | `#8A919C` | `#6E7580` | 비활성 버튼 글자 | (앱 색) |
| `color/action/primary` | `#2350C8` | `#8FB0FF` | PrimaryButton 바탕, TextButton 글자, 대표 코스 강조, 진행 표시 | `primary` (`secondary`·`tertiary`도 같은 값) |
| `color/action/primary-pressed` | `#1A3EA6` | `#B5CBFF` | PrimaryButton 누름 | (앱 색) |
| `color/action/secondary-pressed` | `#E8ECF2` | `#2A2E35` | SecondaryButton·TextButton·CourseCard·ListRow 누름 바탕 | (앱 색) |
| `color/action/disabled` | `#E3E6EB` | `#2A2E35` | 비활성 PrimaryButton 바탕 | (앱 색) |
| `color/border/strong` | `#6B7380` | `#8A929E` | SecondaryButton 테두리, 상태 이름표(알약) 테두리 | `outline` |
| `color/border/subtle` | `#DDE1E7` | `#343942` | 구분선(ConditionRow·ListRow 아래), 카드 테두리, Locator 판 테두리 | `outlineVariant` |
| `color/error` | `#C62828` | `#FF8A80` | 기록 지우기 확인 등 되돌릴 수 없는 동작 | `error` (`onError`는 `#FFFFFF` / `#3B0909`) |
| `color/locator/plate` | `#F6F7F9` | `#F6F7F9` | Locator 판 바탕. **위치 안내 그림의 바탕과 같은 색**이라 그림 가장자리가 보이지 않는다. 야간에도 바꾸지 않는다(1-4절) | (앱 색) |
| `color/locator/ink` | `#2C3038` | `#2C3038` | 판 위에 앱이 직접 쓰는 글자(그림이 없을 때의 대체 문구). 그림의 조작부 색과 같다 | (앱 색) |
| `color/locator/accent` | `#FF8A00` | `#FF8A00` | 참고용. 그림 안의 강조 고리 색. **앱 UI에는 쓰지 않는다** | — |

Material 3 칸 가운데 위 표에 없는 것(`primaryContainer`, `inverseSurface` 등)은 쓰지 않는다. 기본값이 화면에 새어 나오지 않게 `lightColorScheme(...)`·`darkColorScheme(...)`에 `surfaceTint = Color.Transparent`를 준다(카드에 보라색 기운이 끼지 않게).

### 1-2. 판정 상태 8개 (`StateBanner`)

변수 이름: `color/status/<key>/bg`, `…/fg`, `…/icon`. 테두리 색은 `icon`과 같다. 이름표 문구는 용어집 2절의 상태 이름을 따른다.

| 상태 | key | 이름표 | 기호 | bg (L / D) | fg 글자 (L / D) | icon 기호·테두리 (L / D) | 테두리 |
|---|---|---|---|---|---|---|---|
| `PRECHECK` | `precheck` | 확인 중 | 스피너 (원호, 유일한 움직임) | `#EEF0F3` / `#2A2E35` | `#3E4450` / `#D3D8E0` | `#3E4450` / `#D3D8E0` | 2dp 실선 |
| `WAITING` | `waiting` | 기다리는 중 | 채운 원 안에 → | `#E7EEFF` / `#1B2A4D` | `#1D3F99` / `#B9CCFF` | `#2350C8` / `#8FB0FF` | 2dp 실선 |
| `ALREADY_AT_TARGET` | `already` | 이미 되어 있음 | 채운 원 안에 ↻ (껐다 켜기) | `#DFF3F4` / `#123638` | `#0B5F69` / `#8FDCE2` | `#0B7C88` / `#5CC8D1` | 2dp 실선 |
| `PARTIAL` | `partial` | 일부 됨 | 채운 원 안에 ◐ | `#E3F4E8` / `#173322` | `#1E6B3A` / `#9FE0B4` | `#1E7B43` / `#5CCB86` | 2dp 실선 |
| `CORRECTION` | `correction` | 교정 | **삼각형** 안에 ! | `#FFF3C4` / `#3D3000` | `#6B4E00` / `#FFE08A` | `#8F6B00` / `#D9A800` | **3dp** 실선 |
| `DONE` | `done` | 완료 | 흰 원 안에 ✓ | **`#1E7B43`** / `#7FD9A0` (꽉 찬 초록) | `#FFFFFF` / `#0B2E18` | `#FFFFFF` / `#0B2E18` | 없음 |
| `UNVERIFIABLE` | `unverifiable` | 확인 불가 | **점선 원** 안에 ? | `#EFEAFB` / `#2B2147` | `#4B2E99` / `#CDBDFF` | `#6A4FC4` / `#9C86F0` | 2dp **점선** |
| `SKIPPED` | `skipped` | 건너뜀 | 채운 원 안에 » | `#EEF0F3` / `#2A2E35` | `#4A505A` / `#B4BAC4` | `#6B7380` / `#8A929E` | 2dp 실선 |

정한 이유:

- **확인 불가와 미충족(기다리는 중)이 한눈에 다르다**(사용자 플로우 9절): 색(보라 ↔ 파랑), 기호(점선 원 ? ↔ 채운 원 →), 테두리(점선 ↔ 실선) 세 가지가 모두 다르다. 조건 줄에서도 같다(1-3절: 점선 원 ? ↔ 빈 회색 원).
- **완료는 초록**, 일부 됨은 같은 초록 계열의 옅은 바탕. 완료만 바탕을 꽉 채워 멀리서도 구분된다.
- **교정은 노랑(주의)**: 위치 안내 그림의 강조색 `#FF8A00`(주황, 색상각 약 32°)과 헷갈리지 않게 더 노란(약 45°) 어두운 색을 기호에만 쓰고, 바탕은 옅은 노랑이다. 기호 모양도 삼각형이라 그림의 둥근 강조 고리와 다르다.
- 기호 바탕 위 기호 글자(→, ↻ …)의 색은 그 상태의 `bg`다(채운 원의 "구멍" 색).
- DONE은 2초 뒤 자동으로 넘어가므로 이름표("완료")를 크게 키우지 않는다.

### 1-3. 조건 표시 (`ConditionRow`)

변수 `color/mark/*`는 위 상태 색을 가리키는 별칭(alias)이다.

| Figma 변형 `Status=` | 코드 `ConditionStatus` | 표시 (44dp 원) | 기호 색 (변수) | 기본 문구 (`strings.xml`) | 문구 색 |
|---|---|---|---|---|---|
| `Met` | `MET` | 초록으로 채운 원 + ✓ (기호는 `bg/surface` 색) | `mark/met` = `status/partial/icon` | 확인됐어요 | `mark/met` |
| `AlreadyMet` | `ALREADY_MET` | 테두리만 있는 원 + ✓ | `mark/already` = `status/already/icon` | 이미 맞아요 | `mark/already` |
| `Unmet` | `UNMET` | 빈 회색 원(기호 없음) | `mark/unmet` = `text/secondary` | 아직이에요 | `text/secondary` |
| `Toggle` | `TOGGLE_NEEDED` | 테두리 원 + ↻ | `mark/toggle` = `status/already/icon` | 껐다가 다시 켜 주세요 | `mark/toggle` |
| `Unknown` | `UNKNOWN` | **점선** 원 + ? | `mark/unknown` = `status/unverifiable/icon` | 확인할 수 없어요 | `status/unverifiable/fg` |
| `Polling` | (`view.polling == true`) | 24dp 스피너 (Figma에는 ⋯로 표시) | `mark/polling` = `text/secondary` | 확인 중 | `text/secondary` |
| `Wrong` | (없음 — 7절) | 옅은 노랑으로 채운 원 + 노란 테두리 + ! | `mark/wrong` = `status/correction/icon` | 바꿔 주세요 | `status/correction/fg` |

- 원 테두리 3dp. 지금 문구 앞의 글자 기호(`✓ `, `· `, `? `)는 원 안의 기호로 옮기고 문구에서는 뺀다(`strings.xml`의 `condition_*`에서 앞 기호 삭제).
- 폴링 중이면서 상태가 따로 있는 경우(예: `UNMET` + 폴링)는 원 자리에 스피너, 문구는 "아직이에요 (확인 중)"처럼 지금 코드와 같이 붙인다.

### 1-4. 위치 안내 그림과 어울리기

- 그림(`app/src/main/assets/models/reference/assets/*.webp`)은 바탕 `#F6F7F9`, 조작부 `#2C3038`, 강조 `#FF8A00`로 그려져 있다.
- 주간: 앱 바탕 `#F1F3F6` 위에 판(`locator/plate` `#F6F7F9`, 테두리 `border/subtle` 2dp, 모서리 24dp)을 두고 그 안에 그림을 `ContentScale.Fit`으로 넣는다. 판 색과 그림 바탕이 같아 그림의 네모 가장자리가 보이지 않는다. 앱 바탕은 판보다 한 단계 어두워 판이 구분된다.
- **야간**: 그림이 밝은 바탕으로 그려져 있어 판을 어둡게 하면 그림 바탕만 밝은 네모로 뜬다. 그래서 **판을 야간에도 밝게(`#F6F7F9`) 둔다.** 대신 판 크기를 세로형 높이 340dp로 묶어 화면에서 밝은 면적이 커지지 않게 한다. 판 테두리(`border/subtle` Dark `#343942`)가 판과 어두운 바탕 사이를 부드럽게 한다. 야간용 그림(어두운 바탕 판)을 따로 그릴지는 7절.
- 앱은 그림 위에 강조를 덧그리지 않는다. "어디 있어요"를 누르면 그림은 그대로이고 위치 문구를 읽는다(사용자 플로우 5절).

## 2. 글자

Figma 컬렉션 `type`(크기·줄 높이·굵기·글꼴 변수)과 같은 이름의 텍스트 스타일. 모든 크기는 sp(= dp = Figma px).

| 이름 (텍스트 스타일) | 크기 | 굵기 | 줄 높이 | 쓰는 곳 | Material 3 `Typography` |
|---|---|---|---|---|---|
| `display` | 48 | Bold (700) | 60 | SCR-08 차단 문구(가로형), SCR-01 앱 이름 | `displaySmall` |
| `headline` | 38 | Bold (700) | 50 | 화면의 큰 제목(코스 이름, "조작 완료", 연결 오류 제목), SCR-08(세로형) | `headlineMedium` |
| `instruction` | 32 | Bold (700) | 42 | SCR-04 안내 문구 한 문장, SCR-11 질문 | `headlineSmall` |
| `title` | 28 | SemiBold (600) | 38 | TopBar 제목, CourseCard 이름, StateBanner 제목 | `titleLarge` |
| `row` | 26 | Medium (500) | 34 | ConditionRow 조건 이름, ListRow 항목 이름, 묶음 이름 | `titleMedium` |
| `body` | 24 | Regular (400) | 34 | 본문, 위치 문구, StateBanner 설명, TopBar 오른쪽(1/3) | `bodyLarge` |
| `caption` | 22 | Regular (400) | 30 | 보조 문구(예상 시간 "약 1분 · 1단계", ListRow 설명) | `bodyMedium` |
| `label` | 26 | SemiBold (600) | 32 | 버튼 글자 | `labelLarge` |
| `label-sm` | 22 | SemiBold (600) | 28 | 상태 이름표, 조건 상태 문구, VoiceBar, CourseCard 상태 알약 | `labelMedium` |

- 지금 코드보다 작아지는 것은 없다. 본문 24, 제목 28~38은 그대로이고, `bodyMedium`은 21 → 22, `labelLarge`는 22 → 26으로 커진다. 22 미만은 쓰지 않는다.
- 한 화면의 문단은 3줄 이하(POL-W-4). 세로형 내용 너비 777dp에서 `body` 24sp는 한 줄에 한글 약 30자다.
- **글꼴**: Figma는 Asta Sans(Pleos 시스템 UI 서체)로 그렸다. Asta Sans는 SIL Open Font License 1.1로 배포되어(Google Fonts 등록, 저장소 `42dot/Asta-Sans`) 앱에 넣어 배포할 수 있다. 코드에서는 **우선 `FontFamily.Default`(시스템 글꼴)를 쓴다** — Pleos에서 시스템 글꼴이 Asta Sans라면 그대로 같아진다. 에뮬레이터의 시스템 글꼴이 Asta Sans가 아니면 `res/font/`에 Asta Sans(Regular·Medium·SemiBold·Bold)를 넣고 OFL 전문을 함께 둔다(7절). 상표 글꼴이 아니므로 4절 "하지 않는 것"에 걸리지 않는다.

## 3. 간격·크기·모서리

Figma 컬렉션 `space`(간격·크기)와 `radius`.

| 이름 | dp | 쓰는 곳 |
|---|---|---|
| `space/xs` | 4 | 글자 줄 사이 미세 간격 |
| `space/s` | 8 | StateBanner 안 줄 간격, 제목–보조 문구 |
| `space/sm` | 12 | 상태 기호–이름표 |
| `space/m` | 16 | 버튼 사이, VoiceBar 점–글자 |
| `space/ml` | 20 | 화면 본문 요소 사이, ConditionRow 표시–이름, CourseCard 안 |
| `space/l` | 24 | 카드·배너 안쪽 여백, VoiceBar 좌우 |
| `space/xl` | 32 | 버튼 좌우 여백, 가로형 두 칸 사이 |
| `space/xxl` | 48 | 문구만 있는 화면(SCR-01·08·09)의 묶음 사이 |
| `space/screen-h` | 32 | 화면 좌우 여백 |
| `space/screen-v` | 24 | 화면 위아래 여백 (Figma 와이어프레임은 28 — 코드는 24로 맞춘다) |
| `size/touch-min` | 76 | 누를 수 있는 모든 것의 최소 높이 |
| `size/button-h` | 88 | 버튼 높이 (지금 `BigButton`의 최소 64dp에서 올림) |
| `size/topbar-h` | 96 | TopBar 높이 |
| `size/mark` | 44 | ConditionRow 표시 원 |
| `size/state-icon` | 40 | StateBanner 상태 기호 |
| `size/icon` | 36 | 그 밖의 기호 |

| 이름 | dp | 쓰는 곳 |
|---|---|---|
| `radius/sm` | 12 | (예비) 작은 상자 |
| `radius/md` | 16 | 안내 상자(정차 안내 등) |
| `radius/button` | 20 | PrimaryButton·SecondaryButton·TextButton |
| `radius/card` | 24 | CourseCard, StateBanner, Locator 판, 겹쳐 뜨는 창(SCR-07) |
| `radius/full` | 999 | 알약형(VoiceBar, 상태 이름표), 원 |

Compose: `object Space { val s = 8.dp … }`, `object Radius { val card = 24.dp … }`처럼 두고 `Shapes(small = RoundedCornerShape(Radius.button), medium = RoundedCornerShape(Radius.card))`로 `MaterialTheme`에 넣는다.

## 4. 컴포넌트

공통 규칙:

- **누름**: 누르는 동안 `graphicsLayer { scaleX = 0.95f; scaleY = 0.95f }` + 표의 누름 색. 0.25초 안에 반응한다(POL-U-7). 물결(ripple)은 쓰지 않는다(`indication = null`) — 움직임이 늘어나지 않게(POL-U-5). 구현 예: `val pressed by interaction.collectIsPressedAsState()`.
- **비활성**: 누를 수 없고 표의 비활성 색. 축소하지 않는다.
- 터치 대상의 높이는 76dp 이상, 버튼은 88dp.

| 컴포넌트 (Figma 이름) | 지금 코드 | 크기·여백·모서리 | 색 변수 | 변형 | 기호·글자 |
|---|---|---|---|---|---|
| `PrimaryButton` | `BigButton(primary = true)` | 높이 88, 좌우 32, 모서리 20. 너비는 놓인 자리에 맞춤 | 바탕 `action/primary` / 누름 `action/primary-pressed` / 비활성 `action/disabled` + 글자 `text/disabled`. 글자 `text/on-primary` | `State=Default·Pressed·Disabled` | `label` |
| `SecondaryButton` | `BigButton(primary = false)` | 위와 같음, 테두리 2dp | 바탕 `bg/surface`, 테두리 `border/strong`, 글자 `text/primary` / 누름 바탕 `action/secondary-pressed` / 비활성 테두리 `border/subtle` + 글자 `text/disabled` | 위와 같음 | `label` |
| `TextButton` | Material `TextButton` (그만하기, 건너뛰기, "김이 잘 안 걷힐 때") | 높이 88(TopBar 안에서는 TopBar 높이), 좌우 32 | 글자 `action/primary` / 누름 바탕 `action/secondary-pressed` / 비활성 글자 `text/disabled` | 위와 같음 | `label` |
| `TopBar` | `StepContent` 맨 위 `Row`(진행 위치 + 그만하기) 등 화면마다 따로 | 높이 96, 좌우 32, 아래 구분선(`border/subtle`) | 바탕 `bg/surface`, 선 `border/subtle`, 제목 `text/primary`, 오른쪽 `text/secondary` | 없음 | 왼쪽 뒤로·그만하기(`label`), 가운데 제목(`title`), 오른쪽 1/3(`body`) |
| `CourseCard` | `HomeScreen.kt`의 `private fun CourseCard` | 높이 ≥ 76(실제 약 122), 안쪽 24, 모서리 24, 테두리 2dp | 바탕 `bg/surface`, 테두리 `border/subtle`, 이름 `text/primary`, 보조 `text/secondary` / 누름 바탕 `action/secondary-pressed`. 대표 코스(첫 실행)는 테두리 `action/primary` 2dp + 상태 알약에 "여기서 시작" | `State=Default·Pressed` | 이름 `title`, "약 1분 · 1단계" `caption`, 상태 알약(시작 전 / 완료 / 건너뜀 있음) `label-sm`·테두리 `border/strong`·모서리 full. 점수·백분율 없음 |
| `ConditionRow` | `Components.kt`의 `ConditionRow` | 위아래 14, 간격 20, 아래 구분선(`border/subtle`) | 1-3절 | `Status=Unmet·Met·AlreadyMet·Toggle·Wrong·Unknown·Polling` | 표시 원 44, 이름 `row`, 상태 문구 `label-sm`(오른쪽 정렬). 터치 대상 아님 |
| `ListRow` | 설정·소등 확인·SCR-11의 줄 (지금 화면마다 따로) | 높이 ≥ 76, 위아래 20, 아래 구분선(`border/subtle`) | 이름 `text/primary`, 설명 `text/secondary`, 선 `border/subtle` / 누름 바탕 `action/secondary-pressed` | `State=Default·Pressed` (누를 수 있는 줄만) | 이름 `row`, 설명 `caption`, 값 `body` |
| `StateBanner` | 없음 (지금 `MessageBlock`이 문구만 그림) | 안쪽 24, 줄 간격 8, 모서리 24 | 1-2절 | `State=` 8개 + `head`(위 줄 보이기, 기본 켬) | 위 줄: 상태 기호 40dp + 이름표(`label-sm`). 제목 `title`, 설명 `body`(없으면 숨김). SCR-04 밖(SCR-06 켜진 등, SCR-11 마무리)에서는 `head = false`로 색만 빌려 쓴다 |
| `VoiceBar` | `StepScreen.kt`의 `VoiceIndicator` | 높이 64, 좌우 24, 모서리 full | 바탕 `bg/surface-variant`, 글자 `text/primary`(듣는 중) · `text/secondary`(그 밖) | `Mode=Listening·Speaking·Off` | 점 20dp: 듣는 중 = 채운 `action/primary` 점, 재생 중 = 채운 회색 점, 꺼짐 = 빈 원. 점은 깜박이지 않는다. 글자 `label-sm`. 터치 대상 아님(마이크 버튼은 7절) |
| `Locator` | `StepScreen.kt`의 `Locator` | 세로형 높이 340, 가로형 오른쪽 칸 전체 높이. 모서리 24, 테두리 2dp, 그림 `ContentScale.Fit` | 판 `locator/plate`, 테두리 `border/subtle`. 판 아래 위치 문구 `text/secondary` | 없음 | 그림이 없으면 판 안에 문구(`body`, `locator/ink`) |

진행 표시: `CircularProgressIndicator`(굵기 4dp) 색은 `action/primary`(SCR-01) 또는 `status/precheck/icon`(SCR-04 확인 중), 크기 32~40dp.

기호 구현: Material Icons(Apache 2.0)로 그린다. → `Icons.AutoMirrored.Filled.ArrowForward`, ↻ `Icons.Filled.Autorenew`, ◐ `Icons.Filled.Contrast`, ! 삼각형 `Icons.Filled.Warning`, ✓ `Icons.Filled.Check`, ? `Icons.Filled.QuestionMark`(점선 원은 `Canvas`로), » `Icons.Filled.SkipNext`. `Contrast`·`Autorenew`·`QuestionMark`·`SkipNext`는 `material-icons-extended`에 있다 — 의존성을 늘리기 싫으면 해당 아이콘의 벡터만 `res/drawable`로 옮긴다.

## 5. 화면 배치

### 5-1. 세로·가로 나누기

- 기준: **앱 창 너비 ≥ 1200dp면 가로형**, 아니면 세로형. `BoxWithConstraints { val wide = maxWidth >= 1200.dp }`.
- 실측된 두 창(1682 / 841)의 한가운데쯤이라 창이 처음 그려질 때 잠깐 1682×1120이 되는 경우(G1)에도 가로형으로 판정된다. 지금 코드의 `maxWidth > maxHeight`와 두 실측 창에서 결과가 같지만, 창 높이가 바뀌어도 흔들리지 않게 너비 기준으로 바꾼다.
- 바깥 여백: 좌우 32, 위아래 24. 세로형 내용 너비 841 − 64 = **777dp**, 가로형 1682 − 64 = **1618dp**.
- 높이 1210에서 TopBar 96을 빼면 본문 높이는 1114dp(여백 빼면 1066dp).

### 5-2. 화면별

| 화면 | 세로형 841×1210 | 가로형 1682×1210 |
|---|---|---|
| SCR-02 홈 | TopBar(앱 이름 · 설정) / 코스 목록(묶음 이름 + CourseCard, 세로 스크롤, 간격 14) / 아래 고정 "하차 전 소등 확인" SecondaryButton(아래 띠 높이 134) | TopBar / 두 칸: **왼쪽 코스 목록(남는 너비, 약 934)** · 간격 40 · **오른쪽 640 고정**(묶음별 완료 현황, 소등 확인 버튼 아래 붙임) |
| SCR-03 코스 소개 | TopBar(‹ 홈) / 코스 이름(`headline`) → 설명(`body`, 3줄 이하) → 단계 수·시간(`caption`) → Locator 미리 보기(높이 340) → 정차 안내 상자 → (남는 공간) → 시작 PrimaryButton → "김이 잘 안 걷힐 때" TextButton | 두 칸 **왼쪽 721 : 오른쪽 861** (약 45:55, Compose `weight(5f)`·`weight(6f)`), 간격 32. 왼쪽: 이름·설명·정차 안내·버튼. 오른쪽: Locator 미리 보기(칸 전체 높이) |
| SCR-04 단계 진행 | TopBar(× 그만하기 · 코스 이름 · 1/3) / 안내 문구(`instruction`) 또는 StateBanner → Locator(높이 340) → 위치 문구 → 조건 목록 → (남는 공간) → VoiceBar → 버튼 줄(다시 듣기·어디 있어요 Secondary, 건너뛰기 Text, 간격 16) | 두 칸 **왼쪽 721 : 오른쪽 861**, 간격 32. **왼쪽: 안내 문구·StateBanner·조건·VoiceBar·버튼** (운전석에 가까워 손이 닿기 쉽다). **오른쪽: Locator(칸 전체 높이) + 위치 문구** |
| SCR-05 코스 완료 | TopBar(코스 이름) / 결과(✓ 표시 + "조작 완료" `headline` 또는 "건너뛴 단계가 있어요") → 아직 켜져 있는 것(ListRow) → (남는 공간) → 다음 코스 Primary → 홈으로 Secondary → "아직 김이 남아 있어요" Text | 두 칸 **왼쪽(남는 너비, 약 934) : 오른쪽 640**. 왼쪽: 결과와 아직 켜져 있는 것. 오른쪽: 다음 코스(CourseCard)·묶음 현황·버튼 |

- SCR-04 세로형에서 판정 상태가 바뀌면 안내 문구 자리에 StateBanner가 들어오고 안내 문구는 그 위의 작은 글자(`caption`)로 남는다(03 보드). 버튼 줄은 언제나 맨 아래에 고정하고, 내용이 넘치면 가운데 부분만 스크롤한다(지금 코드와 같음).
- 와이어프레임 v0.1의 가로형 SCR-03·04는 그림이 왼쪽이었다. 이번에 **문구·버튼을 왼쪽, 그림을 오른쪽**으로 바꿨다: 운전석이 왼쪽이라 버튼이 가까워지고, 지금 코드(`StepContent`의 가로형)와도 같다. SCR-06 가로형도 같은 순서다.
- 문구만 있는 화면(SCR-01·07·08·09)은 두 방향 모두 가운데 정렬. SCR-08은 바탕 `bg/blocked`, 문구 두 줄(`headline`/세로, `display`/가로 + `body`), 버튼·움직임 없음(POL-D-4). 플랫폼의 주행 중 회색 화면과 같은 회색 톤이다.
- 늘어난 높이(1000 → 1210)는 Locator를 키우는 데(240 → 340) 쓰고, 나머지는 버튼 위 빈 공간으로 둔다(버튼이 화면 아래 같은 자리에 있게).

## 6. Figma 링크

모두 파일 `7nCbKAdriwwQWVaXXhEIyV`, 페이지 "와이어프레임 v0.1".

- 변수 컬렉션 `color`(Light·Dark), `type`, `space`, `radius`와 텍스트 스타일 9개: 파일의 Local variables / Local styles 패널. 변수는 노드가 아니라 링크가 없다. 각 변수에 Android 코드 이름(`MaterialTheme.colorScheme.primary`, `StatusColors.done.bg` 등)을 적어 두었다(Dev Mode에서 보인다).
- 안내: [00 안내](https://www.figma.com/design/7nCbKAdriwwQWVaXXhEIyV/?node-id=3-2)
- 컴포넌트: [01 컴포넌트 보드](https://www.figma.com/design/7nCbKAdriwwQWVaXXhEIyV/?node-id=3-24)
  - [PrimaryButton](https://www.figma.com/design/7nCbKAdriwwQWVaXXhEIyV/?node-id=90-494) · [SecondaryButton](https://www.figma.com/design/7nCbKAdriwwQWVaXXhEIyV/?node-id=90-499) · [TextButton](https://www.figma.com/design/7nCbKAdriwwQWVaXXhEIyV/?node-id=90-504)
  - [TopBar](https://www.figma.com/design/7nCbKAdriwwQWVaXXhEIyV/?node-id=3-33) · [CourseCard](https://www.figma.com/design/7nCbKAdriwwQWVaXXhEIyV/?node-id=91-496) · [ListRow](https://www.figma.com/design/7nCbKAdriwwQWVaXXhEIyV/?node-id=91-502)
  - [ConditionRow](https://www.figma.com/design/7nCbKAdriwwQWVaXXhEIyV/?node-id=94-520) · [StateBanner](https://www.figma.com/design/7nCbKAdriwwQWVaXXhEIyV/?node-id=94-577) · [VoiceBar](https://www.figma.com/design/7nCbKAdriwwQWVaXXhEIyV/?node-id=91-509) · [Locator](https://www.figma.com/design/7nCbKAdriwwQWVaXXhEIyV/?node-id=3-59)
- 화면 보드: [02 세로형](https://www.figma.com/design/7nCbKAdriwwQWVaXXhEIyV/?node-id=4-2) · [03 SCR-04 상태별](https://www.figma.com/design/7nCbKAdriwwQWVaXXhEIyV/?node-id=4-259) · [04 가로형](https://www.figma.com/design/7nCbKAdriwwQWVaXXhEIyV/?node-id=5-263)
- 5절의 화면 프레임:

| 화면 | 세로형 841×1210 | 가로형 1682×1210 |
|---|---|---|
| SCR-02 | [4:14](https://www.figma.com/design/7nCbKAdriwwQWVaXXhEIyV/?node-id=4-14) | [5:267](https://www.figma.com/design/7nCbKAdriwwQWVaXXhEIyV/?node-id=5-267) |
| SCR-03 | [4:71](https://www.figma.com/design/7nCbKAdriwwQWVaXXhEIyV/?node-id=4-71) | [9:406](https://www.figma.com/design/7nCbKAdriwwQWVaXXhEIyV/?node-id=9-406) |
| SCR-04 WAITING | [4:273](https://www.figma.com/design/7nCbKAdriwwQWVaXXhEIyV/?node-id=4-273) | [5:336](https://www.figma.com/design/7nCbKAdriwwQWVaXXhEIyV/?node-id=5-336) |
| SCR-04 CORRECTION | [4:421](https://www.figma.com/design/7nCbKAdriwwQWVaXXhEIyV/?node-id=4-421) | [5:379](https://www.figma.com/design/7nCbKAdriwwQWVaXXhEIyV/?node-id=5-379) |
| SCR-05 | [4:132](https://www.figma.com/design/7nCbKAdriwwQWVaXXhEIyV/?node-id=4-132) | [5:425](https://www.figma.com/design/7nCbKAdriwwQWVaXXhEIyV/?node-id=5-425) |
| SCR-08 | [4:207](https://www.figma.com/design/7nCbKAdriwwQWVaXXhEIyV/?node-id=4-207) | [9:439](https://www.figma.com/design/7nCbKAdriwwQWVaXXhEIyV/?node-id=9-439) |

- 야간 예시(03 보드 끝, Dark 모드 적용): [WAITING](https://www.figma.com/design/7nCbKAdriwwQWVaXXhEIyV/?node-id=97-521) · [CORRECTION](https://www.figma.com/design/7nCbKAdriwwQWVaXXhEIyV/?node-id=97-540) · [DONE](https://www.figma.com/design/7nCbKAdriwwQWVaXXhEIyV/?node-id=97-559) · [UNVERIFIABLE](https://www.figma.com/design/7nCbKAdriwwQWVaXXhEIyV/?node-id=97-574)

## 7. 정하지 못한 것

| # | 항목 | 디자인에 걸리는 곳 | 정하는 방법 |
|---|---|---|---|
| 1 | 앱 이름 (PRD D1) | SCR-01 "(앱 이름)", SCR-02 TopBar 제목(지금 "첫 탑승 조작 실습" 가칭), 앱 아이콘 | 팀 결정 |
| 2 | 기준 차종 (PRD D2) | 위치 안내 그림 전체, SCR-10 "기준 차종", 그림 판의 비율(지금 그림은 CRS-01 공조 8장만) | 팀 결정 |
| 3 | 듣기 시작 방식 (PRD D5) | 마이크 버튼 방식이면 VoiceBar에 누르는 변형(`Mode=Idle`, 높이 76 이상)이 필요하다 | 실측 T9 |
| 4 | 배지 디자인과 묶음 이름 (PRD D6) | SCR-02 묶음 현황, SCR-07 | 팀 결정 |
| 5 | 야간용 위치 안내 그림 | 지금은 야간에도 밝은 판(1-4절). 야간 그림(바탕 `#2A2E35`, 조작부 `#E6E8EC`, 강조 `#FF8A00` 그대로)을 따로 그리면 판을 `bg/surface`로 바꿀 수 있다 | 실차·에뮬레이터 야간 화면에서 눈부심 확인 뒤 |
| 6 | 에뮬레이터의 시스템 글꼴 | Asta Sans가 아니면 글꼴 파일을 앱에 넣는다(2절) | 에뮬레이터에서 확인 |
| 7 | 상태 이름표 "교정" | 화면에 보이는 낱말이다. 사용자를 평가하는 말로 읽히지 않는지(POL-W-6) | 사용성 테스트. 바꾸면 용어집과 함께 |
| 8 | 조건 줄 `Wrong`(교정 대상) | 와이어프레임 CORRECTION 화면은 오조작 조건에 "!"를 단다. 지금 `ConditionStatus`에는 이 값이 없다. 엔진에 값을 더할지, 화면이 교정 상태일 때 대상 조건만 바꿔 그릴지 | 기술 설계 담당 |
| 9 | SCR-02 세로형의 묶음 현황 | 세로형에는 묶음 이름 옆 "배지 ○ · 0/3"만 있다. 진행률이 경쟁처럼 보이지 않는지(사용자 플로우 7절) | D6과 함께 |
| 10 | 차단 화면 회색 | 플랫폼 회색 화면의 실제 색을 재지 않았다. `bg/blocked`는 근사값 | 에뮬레이터 화면 캡처에서 색을 재 맞춘다 |
