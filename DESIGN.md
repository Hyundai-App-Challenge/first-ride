---
version: alpha
name: first-ride-practice
description: "Pleos Connect 앱 창 안에서 도는 차량 내 실습 앱. 바탕·표면·글씨는 Pleos 회색 토큰을 그대로 쓰고, 색은 '차량 신호로 확인됨(초록)', '지금 볼 곳(노랑)', '되돌릴 수 없음·오류(빨강)' 세 가지 뜻에만 쓴다. 글씨에는 색을 넣지 않는다. 글자·간격·크기·배치는 docs/08-design-tokens.md를 따른다."

# 키는 Figma 컬렉션 `color`의 변수 이름(`/` 대신 `.`)이다. 값은 light / dark 순서.
colors:
  bg.app: { light: "#F7F8FA", dark: "#1D1E21" }                 # Pleos background_primary
  bg.surface: { light: "#FFFFFF", dark: "#131417" }             # Pleos background_secondary
  bg.card: { light: "#FFFFFF", dark: "#313236" }                # Pleos background_popup
  bg.surface-variant: { light: "#EDEEF2", dark: "#313236" }     # Pleos surface_high
  bg.blocked: { light: "#EDEEF2", dark: "#313236" }
  text.primary: { light: "#131417", dark: "#FFFFFF" }           # Pleos text_dark_primary
  text.secondary: { light: "#131417D6", dark: "#FFFFFFD6" }     # 84%
  text.tertiary: { light: "#131417A3", dark: "#FFFFFFA3" }      # 64%
  text.disabled: { light: "#13141752", dark: "#FFFFFF52" }      # 32%
  text.on-primary: { light: "#FFFFFF", dark: "#131417" }
  action.primary: { light: "#44464E", dark: "#E0E1E6" }         # Pleos button_filled_enabled
  action.primary-pressed: { light: "#313236", dark: "#C7C9CE" } # Pleos button_filled_pressed
  action.secondary: { light: "#E0E1E6", dark: "#313236" }
  action.secondary-pressed: { light: "#C7C9CE", dark: "#44464E" }
  action.disabled: { light: "#EDEEF2", dark: "#313236" }
  border.subtle: { light: "#E0E1E6", dark: "#44464E" }          # Pleos surface_accent
  error: { light: "#FE3D16", dark: "#FF4C28" }                  # Pleos informative_negative
  status.confirmed: { light: "#02C265", dark: "#00E074" }       # Pleos informative_active
  status.attention: { light: "#FFC224", dark: "#FFC224" }       # Pleos regulation_yellow
  status.on: "#131417"                                          # Pleos basic_static_dark_100
  locator.plate: "#F6F7F9"
  locator.ink: "#2C3038"
  locator.accent: "#FFC224"

components:
  StateBanner-correction:
    backgroundColor: "{colors.status.attention}"
    textColor: "{colors.status.on}"
    border: none
  StateBanner-other:
    backgroundColor: "{colors.bg.surface-variant}"
    textColor: "{colors.text.primary}"
    border: none
  SecondaryButton:
    backgroundColor: "{colors.action.secondary}"
    textColor: "{colors.text.primary}"
    border: none
  TextButton:
    textColor: "{colors.text.secondary}"
  CourseCard:
    backgroundColor: "{colors.bg.card}"
    border: none
---

# DESIGN.md — 첫 탑승 조작 실습 (가칭)

- 문서 상태: 초안 v0.2 (2026-11-15)
- 이 문서가 정하는 것: **색과 색을 쓰는 규칙**. 글자·간격·크기·모서리·컴포넌트 규격·화면 배치는 [디자인 값](docs/08-design-tokens.md)이 정한다. 두 문서가 어긋나면 색은 이 문서, 나머지는 08이 기준이다.
- 같은 값이 세 곳에 있다: 이 문서, Figma 파일 `7nCbKAdriwwQWVaXXhEIyV`의 변수 컬렉션 `color`, `app/.../ui/Theme.kt`. 값을 바꿀 때는 세 곳을 함께 고친다.
- 근거: [Pleos Connect UI Design Guide](https://document.pleos.ai/docs/connect/guide/docs-design/foundations/colors)의 Colors, Typography, Shadow, Interaction, Basic button 문서(2026-11-15 확인). 색 값은 문서의 표 그림에서 읽었다.
- 표기: **[가정]** 은 Pleos 문서에 없어 팀이 정한 값, **[미확인]** 은 확인하지 못한 것이다.

---

## 1. 방향

이 앱은 Pleos의 상태 표시줄과 하단 바 사이에 뜬다. 그래서 앱만의 팔레트를 만들지 않고 Pleos 토큰 위에 얹는다.

1. **바탕·표면·글씨는 Pleos 회색 그대로.** 푸른 기가 도는 회색(`#F7F8FA`, `#131417`)은 Pleos의 기본값이다. 무채색으로 바꾸지 않는다.
2. **글씨에는 색을 넣지 않는다.** Pleos의 글씨 토큰은 검정 또는 흰색에 투명도만 다른 4단계뿐이다. 초록 글씨, 황토 글씨, 파란 글씨 버튼을 만들지 않는다.
3. **색은 뜻이 있을 때만, 컴포넌트당 한 군데.** 초록은 "차량 신호로 확인됨", 노랑은 "지금 볼 곳", 빨강은 "되돌릴 수 없음·오류". 그 밖의 색은 없다.
4. **상태는 기호와 문구가 먼저, 색은 보조.** POL-U-4. 색을 빼도 상태가 구분돼야 한다.
5. **야간에도 색을 흐리게 만들지 않는다.** Pleos는 야간에서 채도를 유지한다. 파스텔 변형을 만들지 않는다.

## 2. 색

Figma 컬렉션 `color`, 모드 `Light`·`Dark`. 이름은 Figma 변수 이름, 오른쪽 끝은 Compose에서의 자리다.

### 2-1. 바탕과 표면

| Figma 변수 | Light | Dark | Pleos 토큰 | 쓰는 곳 | Compose |
|---|---|---|---|---|---|
| `bg/app` | `#F7F8FA` | `#1D1E21` | `background_primary` | 화면 바탕 | `background` |
| `bg/surface` | `#FFFFFF` | `#131417` | `background_secondary` | TopBar | `surface` (`surfaceContainer*`도 같은 값) |
| `bg/card` | `#FFFFFF` | `#313236` | `background_popup` | CourseCard, 겹쳐 뜨는 창(SCR-07) | `AppColors.card` |
| `bg/surface-variant` | `#EDEEF2` | `#313236` | `surface_high` | StateBanner(교정 밖), VoiceBar, 안내 상자, 상태 알약 | `surfaceVariant` |
| `bg/blocked` | `#EDEEF2` | `#313236` | `surface_high` | SCR-08 화면 전체 | `AppColors.blockedBg` |
| `border/subtle` | `#E0E1E6` | `#44464E` | `surface_accent` | 구분선만(TopBar 아래, ConditionRow·ListRow 아래) | `outlineVariant` |

- 카드, 배너, 판, 버튼에 **테두리를 두르지 않는다.** 구분은 표면 단계로 한다.
- 야간의 `bg/surface`(`#131417`)는 화면 바탕보다 어둡다. 카드처럼 바탕 위에 떠 보여야 하는 것은 `bg/card`를 쓴다.
- 주간의 흰 카드와 화면 바탕은 대비가 1.05:1이다. Pleos의 기본 조합이고, 카드의 내용(제목, 기호)으로 구분한다.

### 2-2. 글씨와 아이콘

| Figma 변수 | Light | Dark | 쓰는 곳 | 대비 (바탕 `bg/app` · `bg/surface-variant`) |
|---|---|---|---|---|
| `text/primary` | `#131417` | `#FFFFFF` | 제목, 안내 문구, 조건 이름, 버튼 글자 | 17.3 · 15.9 / 16.7 · 12.8 |
| `text/secondary` | `#131417` 84% | `#FFFFFF` 84% | 본문, 상태 문구, TextButton, 위치 문구 | 11.0 · 10.3 / 12.0 · 9.5 |
| `text/tertiary` | `#131417` 64% | `#FFFFFF` 64% | 보조 문구(예상 시간), 진행 위치 1/3, 빈 원·점선 원 | 5.4 · 5.3 / 7.5 · 6.3 |
| `text/disabled` | `#131417` 32% | `#FFFFFF` 32% | 비활성 글자만. 읽어야 하는 글자에 쓰지 않는다 | 2.1 / 2.9 |
| `text/on-primary` | `#FFFFFF` | `#131417` | PrimaryButton 글자 | 9.4 / 14.1 |

투명도는 Pleos 토큰과 같다. 아이콘도 같은 단계를 쓴다.

### 2-3. 버튼

| Figma 변수 | Light | Dark | 쓰는 곳 |
|---|---|---|---|
| `action/primary` | `#44464E` | `#E0E1E6` | PrimaryButton 바탕 (Pleos `button_filled_enabled`) |
| `action/primary-pressed` | `#313236` | `#C7C9CE` | PrimaryButton 누름 (Pleos `button_filled_pressed`) |
| `action/secondary` | `#E0E1E6` | `#313236` | SecondaryButton 바탕 **[가정]** |
| `action/secondary-pressed` | `#C7C9CE` | `#44464E` | SecondaryButton 누름, TextButton·CourseCard·ListRow 누름 바탕 |
| `action/disabled` | `#EDEEF2` | `#313236` | 비활성 PrimaryButton 바탕. 글자는 `text/disabled` |

- 주요 버튼도 회색이다. Pleos 버튼에는 파랑이 없다.
- SecondaryButton은 Pleos Basic button 기본형처럼 회색 채움이고 외곽선이 없다. Pleos는 더 옅은 반투명 회색을 쓰는데, 앱 화면에서는 버튼 자리가 보이도록 한 단계 진하게 했다. Pleos design assets의 실제 값을 확인하면 바꾼다 **[미확인]**.

### 2-4. 상태색

| Figma 변수 | Light | Dark | Pleos 토큰 | 뜻 |
|---|---|---|---|---|
| `status/confirmed` | `#02C265` | `#00E074` | `informative_active` | 차량 신호로 확인된 것 |
| `status/attention` | `#FFC224` | `#FFC224` | `regulation_yellow` | 지금 볼 곳: 교정, 켜져 있는 등, 위치 강조 |
| `error` | `#FE3D16` | `#FF4C28` | `informative_negative` | 되돌릴 수 없는 동작, 연결 오류 |
| `status/on` | `#131417` | `#131417` | `basic_static_dark_100` | 위 세 색 위의 글자·기호 |

`regulation_*` 토큰을 앱 화면에 써도 되는지는 **[미확인]** 이다. 쓸 수 없다고 확인되면 `status/attention`을 뒤집힌 표면(주간 `#131417` 바탕에 흰 글자, 야간 `#FFFFFF` 바탕에 `#131417` 글자)으로 바꾸고 `!` 기호로 구분한다.

### 2-5. 위치 안내 그림

| Figma 변수 | 값 (두 테마 같음) | 쓰는 곳 |
|---|---|---|
| `locator/plate` | `#F6F7F9` | 그림 판 바탕. 그림의 바탕색과 같아야 그림 가장자리가 보이지 않는다 |
| `locator/ink` | `#2C3038` | 그림이 없을 때 판 안의 대체 문구 |
| `locator/accent` | `#FFC224` | 그림 속 강조 고리. 앱 UI에서는 쓰지 않는다. 그림에서는 `#131417` 외곽선 2px과 함께 그린다 |

그림은 밝은 바탕으로 그렸기 때문에 판은 야간에도 밝다. 그래서 야간에는 판이 교정 배너보다 밝은 유일한 면이다. 판 높이를 세로형 340dp로 묶어 밝은 면적을 줄인다. 야간용 그림을 따로 둘지는 08 7절 5번에서 정한다.

### 2-6. 색 사용 규칙

1. **상태색은 채운 도형으로만 쓴다.** 글자와 얇은 선에 쓰지 않는다. 주간 바탕에서 초록은 2.2:1, 노랑은 1.5:1이라 읽히지 않는다.
2. **상태색 위에는 항상 `status/on`(`#131417`).** 흰색은 초록 위 2.4:1, 노랑 위 1.6:1, 빨강 위 3.6:1로 모두 기준에 못 미친다. `#131417`은 7.8, 11.4, 5.2다.
3. **상태 기호의 대비는 안쪽 기호가 맡는다.** 주간의 초록 원은 바탕 대비가 2.2:1이지만 안쪽 ✓가 7.8:1이다. 노랑 원은 바탕 대비가 더 낮아 `#131417` 외곽선 2dp를 두른다. 큰 면(교정 배너)에는 두르지 않는다.
4. **채움과 같은 색 테두리를 함께 쓰지 않는다.**
5. **한 화면의 상태색은 두 가지까지.** SCR-04 교정이라면 초록(확인된 조건)과 노랑(교정)이다.
6. **진행 표시(스피너)와 VoiceBar의 점은 `text/primary`·`text/secondary`.** 색을 쓰지 않는다.

## 3. 컴포넌트의 색

크기·여백·모서리·글자는 08 4절이 기준이다. 여기서는 색만 정한다.

### StateBanner

| 상태 | 바탕 | 상태 기호 (40dp) | 이름표·제목·설명 |
|---|---|---|---|
| `CORRECTION` | `status/attention` | 삼각형 ! (`status/on`) | 모두 `status/on` |
| `DONE` | `bg/surface-variant` | `status/confirmed` 채운 원 + ✓(`status/on`) | `text/primary` |
| `PARTIAL` | `bg/surface-variant` | `status/confirmed` 채운 원 + ◐(`status/on`) | `text/primary` |
| `WAITING` | `bg/surface-variant` | 채운 원 + →. 원 `text/secondary`, 기호 `bg/surface-variant` | `text/primary` |
| `ALREADY_AT_TARGET` | `bg/surface-variant` | 채운 원 + ↻. 색은 WAITING과 같음 | `text/primary` |
| `PRECHECK` | `bg/surface-variant` | 스피너 `text/secondary` | `text/primary` |
| `SKIPPED` | `bg/surface-variant` | 채운 원 + ». 원 `text/tertiary` | `text/primary` |
| `UNVERIFIABLE` | `bg/surface-variant` | 점선 원 + ?. `text/tertiary` | `text/primary` |

- 모든 상태에서 **테두리가 없다.**
- 교정 배너는 주간과 야간이 같은 색이다. 야간용으로 어둡게 만든 겨자색을 두지 않는다.
- 확인 불가는 빨강을 쓰지 않는다. 사용자 잘못도 고장도 아니다.
- 이름표 알약(`label-sm`)은 테두리가 없고, 글자는 그 배너의 글자색이다.

### ConditionRow

| Figma `Status=` | 표시 (44dp 원) | 조건 이름 | 오른쪽 상태 문구 |
|---|---|---|---|
| `Met` | `status/confirmed` 채운 원 + ✓(`status/on`) | `text/primary` | `text/secondary` |
| `AlreadyMet` | 빈 원 3dp + ✓, `text/secondary` | `text/primary` | `text/secondary` |
| `Unmet` | 빈 원 3dp, `text/tertiary` | `text/primary` | `text/tertiary` |
| `Toggle` | 빈 원 3dp + ↻, `text/secondary` | `text/primary` | `text/primary` |
| `Unknown` | 점선 원 3dp + ?, `text/tertiary` | `text/secondary` | `text/secondary` |
| `Polling` | 스피너 24dp, `text/secondary` | `text/primary` | `text/tertiary` |
| `Wrong` | `status/attention` 채운 원 + `#131417` 외곽선 2dp + ! | `text/primary` | `text/primary`, 굵게 |

- 오른쪽 문구에 상태색을 쓰지 않는다. 색은 왼쪽 기호가 이미 말했다.
- "아직"(실선 빈 원)과 "확인할 수 없음"(점선 원 ?)은 색 없이도 구분된다.
- 초록 채움은 신호로 확인된 것에만 쓴다. `AlreadyMet`은 사용자가 조작한 것이 아니므로 채우지 않는다.

### 그 밖의 컴포넌트

| 컴포넌트 | 색 |
|---|---|
| PrimaryButton | 바탕 `action/primary` / 누름 `action/primary-pressed` / 비활성 `action/disabled` + `text/disabled`. 글자 `text/on-primary`. 테두리 없음 |
| SecondaryButton | 바탕 `action/secondary` / 누름 `action/secondary-pressed` / 비활성 `action/disabled` + `text/disabled`. 글자 `text/primary`. 테두리 없음 |
| TextButton | 글자 `text/secondary` / 누름 바탕 `action/secondary-pressed` / 비활성 `text/disabled` |
| 되돌릴 수 없는 동작의 확인 버튼(기록 지우기) | 바탕 `error`, 글자 `status/on` |
| TopBar | 바탕 `bg/surface`, 아래 선 `border/subtle`, 제목 `text/primary`, 오른쪽 1/3 `text/tertiary` |
| CourseCard | 바탕 `bg/card`, 테두리 없음, 이름 `text/primary`, 보조 `text/tertiary`. 상태 알약은 `bg/surface-variant` + `text/secondary`. 완료한 코스는 알약 앞에 `status/confirmed` ✓ 기호. 대표 코스(첫 실행)는 알약을 `action/primary` + `text/on-primary`("여기서 시작")로 바꾼다. 테두리로 강조하지 않는다 |
| StatePanel ("지금 차 상태" 판) | 바탕 `bg/card`, 테두리·그림자 없음. 제목 `text/primary`, 조건 줄은 ConditionRow와 같다, 도움말 `text/tertiary`. 새 색이 없다 |
| ListRow | 이름 `text/primary`, 설명 `text/tertiary`, 값 `text/secondary`, 선 `border/subtle` |
| VoiceBar | 바탕 `bg/surface-variant`. 점: 듣는 중 = 채운 `text/primary`, 재생 중 = 채운 `text/tertiary`, 꺼짐 = 빈 원 `text/tertiary`. 글자 `text/primary`(듣는 중)·`text/secondary`(그 밖) |
| Locator | 판 `locator/plate`, 테두리 없음. 판 아래 위치 문구 `text/secondary` |
| 진행 표시 | `text/secondary` |
| StatusMark (홈 진행 현황) | 완료 = `status/confirmed` 채운 원 + ✓(`status/on`). 건너뜀 있음 = 빈 원 3dp + », `text/secondary`. 시작 전 = 빈 원 3dp, `text/tertiary`. 색 없이 모양으로 구분된다 |
| 진행 현황 ("익힌 조작") | 바탕 `bg/card`, 테두리 없음, 제목 `text/primary` |
| NextCourseCard | CourseCard와 같다(바탕 `bg/card`, 누름 `action/secondary-pressed`). 이름표 알약은 `bg/surface-variant` + `text/secondary`, 기록이 없으면 `action/primary` + `text/on-primary`("여기서 시작"). 그림 판 `locator/plate`. 이름 `text/primary`, 설명 `text/secondary`, 분량 `text/tertiary` |

### 화면별

| 화면 | 규칙 |
|---|---|
| SCR-02 홈 (가로형) | 진행 현황의 초록은 완료한 코스의 ✓ 기호에만 쓴다. 숫자·막대가 없다. 모두 완료면 카드 대신 문구 한 줄(`text/secondary`) |
| SCR-04 `DONE` | 배너의 초록 ✓ 기호와 "조작 완료". 화면 전체를 초록으로 칠하지 않는다 |
| SCR-05 코스 완료 | 결과 기호는 `status/confirmed` 채운 원 + ✓. 건너뛴 단계가 있으면 빈 원 + » |
| SCR-06 소등 확인 | 켜져 있는 등은 노랑 ! 기호 + "켜져 있어요"(`text/primary`), 꺼진 등은 초록 ✓, 읽지 못한 등은 점선 ? |
| SCR-07 배지 | `bg/card`. 배지는 단색 선 그림. 반짝임, 색종이, 별점이 없다 |
| SCR-08 주행 중 차단 | 바탕 `bg/blocked`, 문구 `text/primary`. 기호와 색이 없다 |
| SCR-09 연결 오류 | `error` 채운 원 + !(`status/on`), 제목 `text/primary`. 빨간 글자와 빨간 바탕을 쓰지 않는다 |
| SCR-10 설정 | 스위치는 Pleos 컴포넌트 색. 기록 지우기 확인만 `error` 버튼 |

## 4. 깊이

- 기본은 평면이다. 카드, 배너, 판에 그림자를 넣지 않는다.
- 그림자는 화면 위에 뜨는 것(토스트, SCR-07 창)에만 쓴다. 값은 Pleos Shadow 문서를 따른다: Level 2 = Y 8 Blur 32 검정 16% + Y 2 Blur 2 검정 8%, Level 3 = Y 20 Blur 40 검정 24% + Y 2 Blur 4 검정 20%.

## 5. 할 것과 하지 않을 것

### 할 것

- 글자는 `text/primary`~`text/tertiary`, `text/on-primary`, `status/on`만 쓴다.
- 상태색은 채운 도형에, 그 위 기호는 `#131417`로.
- 색을 뺀 흑백 화면에서도 상태가 구분되는지 확인한다.
- 주간과 야간에서 같은 변수 이름을 쓴다. 값만 모드가 바꾼다.

### 하지 않을 것

- 연한 색 바탕 + 같은 색 진한 테두리 + 같은 색 글자로 된 상자.
- 초록 "확인", 황토 "교정 문구", 파란 "건너뛰기" 같은 색 글자.
- 야간용 파스텔 변형(민트, 연노랑, 연보라)과 어둡게 만든 겨자색 바탕.
- 외곽선 버튼, 테두리로 강조한 카드.
- 그라데이션, 글로우, 장식용 그림자.
- 점수, 별, 등급, 진행률 백분율(용어집의 "쓰지 않는 말").
- 반복 애니메이션, 깜빡이는 표식(POL-U-5).
- 새 색 추가. 필요하면 이 문서를 먼저 고친다.

## 6. 확인할 것

| # | 확인할 것 | 방법 |
|---|---|---|
| D1 | `regulation_yellow`를 앱 화면에 써도 되는가 | Colors 문서의 Regulation 설명, 심사 가이드. 안 되면 2-4절의 대안 |
| D2 | Pleos Basic button의 실제 색(`button_basic_*`, `button_filled_*`) | design assets의 버튼 컴포넌트. 2-3절의 `action/secondary`가 가정이다 |
| D3 | 야간용 위치 안내 그림 | 08 7절 5번 |
