# MusicTimely Android 디자인 사양 (피그마 추출본)

- 원본: Figma `Music-Timely` (`0tVXNjTmVjGrpIjPcm977K`)
- 페이지: `Design System`(0:1), `Assets / Component`(1:2), `Gui`(1:3), `User Flow`(1:4)
- 추출: Gui·에셋은 iOS 추출본(2026-09-30) 승계. Design System·User Flow·와이어프레임은 2026-10-10 MCP로 구조 확인
- 연결 상태(2026-10-10): Figma MCP 연결됨, 계정 devgb, Starter 요금제 View 좌석 → **호출 한도 작음. 이 문서 먼저, MCP는 누락 항목만**
- 기준 캔버스 390 × 844pt → Android는 **1pt = 1dp, 글자 pt = sp**로 옮김

## 우선순위

| 영역 | 따를 문서 |
|---|---|
| 색·폰트·그림자·모서리·레이아웃·모션 | 이 문서 + 피그마 `Gui`·`Design System` |
| 문구·표시 진실성·기능 동작 | SRS Android (`../SRS-adhd-music-timer-android-v1.md`) |
| 접근성 | SRS Android §11 (**48dp** 터치, 대비 4.5:1, 글꼴 200%, 모션 줄이기) |
| 화면 구조·이동 | 와이어프레임 W01~W10 → 단, Android 기능(곡 세기·재생 버튼)은 SRS 우선 |

- 목업의 `Slow Tide`, `LUMEN FIELD`, `9:24`, "기획안 첫 문단 쓰기"는 예시 데이터

## 토큰 (Design System 0:1 · `MT / 01 Foundations` 4:33)

### 색

| 토큰 | Light | Dark | 용도 |
|---|---|---|---|
| background | `#ECECEC` | `#26272C` | 화면 배경 |
| surface | `#EEEEEE` | 배경 계열 | 뉴모피즘 버튼·카드 |
| field | `#FFFFFF` | `#303138` | 입력 필드, 저장된 생각 |
| ink | `#26262C` | `#F3F3F5` | 본문·제목 |
| muted | `#64656E` | `#B4B5BE` | 보조 텍스트 |
| data | | `#1A1B1F` | Dark 데이터 카드 |
| contrast.charcoal | `#232429` | | 결과 비교 카드 |
| contrast.navy | `#1B1B33` | | 보조 버튼, Navy 테마 강조 |
| vinyl | `#0B0B0D` | | 레코드 본체 |
| accent.orange / coral / red | `#FF9C3F` / `#FF7544` / `#FF3B4D` | 같음 | sunset 그라데이션 (가로, coral 48%) |
| accent.icon | `#F5474F` | 같음 | 현재 칸 outline 1.6dp, 아이콘 |

- sunset은 CTA·선택·진행 강조에만. 화면 배경은 무채색
- 테마 Light(기본) / Navy / Dark. V1 확정 전 (PRD 열린 질문 2) → `Theme.kt`에서 교체 가능하게
- **미해결:** 흰 글자 ↔ sunset 대비 2.08~3.51:1 (< 4.5:1). 디자이너 확인 필요

### 그림자 (뉴모피즘)

| 토큰 | 값 (x, y, blur, color) |
|---|---|
| raised.light | (-9,-9,20,`#FFFFFF` 95%) + (10,12,26,`#92949B` 38%) |
| inset.light | inner (-5,-5,11,`#FFFFFF` 95%) + inner (5,5,11,`#92949B` 35%) |
| raised.dark | (-7,-7,16,`#FFFFFF` 5%) + (9,11,22,`#000000` 50%) |
| inset.dark | inner (-4,-4,10,`#FFFFFF` 5%) + inner (5,5,11,`#000000` 50%) |
| accent.glow | (0,12,12,`#FF5046` 27%) 주 버튼 / (0,10,10,`#FF5046` 30%) 선택 칩 |
| vinyl | (-7,-7,18,`#FFFFFF` 24%) + (0,24,38,`#000000` 30%) |
| field | (0,8,10,`#96989E` 18%) |

- Compose: `Modifier.drawBehind` + `Paint.setShadowLayer`(이중 그림자), inset은 `clipPath` 내부 그리기. 텍스트 그림자 없음

### 폰트

- 한글 IBM Plex Sans KR (400·500·700), 숫자·영문 Montserrat (300~600, variable)
- 파일: `app/src/main/res/font/` (`ibm_plex_sans_kr_{regular,medium,bold}.ttf`, `montserrat_variable.ttf`). 라이선스 `licenses/`
- 한 문장 안 혼용: 한글 Plex, 숫자·영문 Montserrat → `AnnotatedString` span
- 타이머 숫자 `fontFeatureSettings = "tnum"`

| 역할 | sp/행간 | 굵기 |
|---|---|---|
| 화면 제목 | 24/34 | Plex Bold |
| 결과 작업 제목 | 26/37 | Plex Bold |
| 섹션 제목 | 21/30 | Plex Bold |
| 진행 패널 제목 | 22/31 | Plex Bold |
| 곡 수 강조 | 32/45 | Plex Medium + Montserrat Medium |
| 다이얼 숫자 | 38/54 | Montserrat Light |
| 칩 숫자 | 24/34 | Montserrat Light |
| 결과 지표 | 34/48 | Montserrat Medium |
| 입력·버튼 | 17/24 | Plex Medium |
| 본문 | 14/20, 15/21, 16 | Plex Medium·Regular |
| 보조·캡션 | 12/17, 13/19, 11/16 | Plex Regular |
| 단위 라벨 (MIN, SET) | 9/13, 10/14 | Montserrat SemiBold |

### 레이아웃

| 항목 | 값 |
|---|---|
| 간격 | 4·8·12·16·20·24·32·48 |
| 모서리 | 16 / 24 / 26 / 30 / pill 999 |
| 좌우 여백 | 카드 20, 텍스트·버튼 24 |
| 터치 | 피그마 최소 44 → **Android 48dp로 상향**. 주요 조작 60~84 |
| 원형 컨트롤 | 44 → 48dp (gear·back·more·close) |
| 시간 칩 | 70, 간격 21 |
| 세션 컨트롤 | plus·stop 60, pause·play 84 |
| 입력 필드 | 350×58, r29 |
| 분→곡 카드 | 350×188, r30 |
| 진행 패널 | 350×134, r26 |
| 결과 비교 카드 | 350×182, r26 |
| 음악 앱 행 | 350×68, r34 |
| 주 버튼 / 보조 버튼 | 342×58 r29 / 342×46 r23 |
| 진행 칸 | 38×10, 간격 5, 트랙 306×20 inset, r5 |
| 안전 영역 | 고정값 대신 `WindowInsets.safeDrawing` |

## 컴포넌트 (`Assets / Component` 1:2 → `lock-screen-and-components.png`)

| 피그마 | Compose 컴포넌트 | 상태 |
|---|---|---|
| Actions / 주 버튼 | `PrimaryButton` | Default·Pressed·Disabled |
| Actions / 시간 칩 | `TimeChip` | 기본·선택(sunset) |
| Actions / 원형 아이콘 | `CircleIconButton` | back·gear·more·plus·pause·stop·play·close |
| Building blocks / 분→곡 다이얼 카드 | `TranslationCard` | Light·Navy·Dark |
| Building blocks / 진행 패널 | `ProgressPanel` | 칸 막대, 현재 칸 outline |
| Building blocks / 결과 비교 카드 | `ComparisonCard` | 문구는 SRS 기준 (아래 충돌표) |
| Building blocks / 작업 필드 | `TaskField` | Light·Navy·Dark |
| States / 마지막 곡 | `ProgressPanel(lastSong)` | 마지막 칸만 outline |
| 레코드·톤암 | `Turntable` | 톤암 30°(레코드 위) / 0°(거치) |
| Android system template | 진행 알림 (`notify`) | 실행 중·멈춤·잠금 3종 |

- 구성 규칙: 입력·환산 카드·진행 패널·결과 카드·바이닐은 마스터 인스턴스로 재사용, Theme Light/Navy/Dark

## 화면 (Gui 1:3)

| 파일 | 노드 | SCR | 구성 |
|---|---|---|---|
| `screens/light-01-home.png` | 4:428 | SCR-01 | 제목+설정, 작업 필드, 10/25/40/+SET, 다이얼 카드, 음악 앱 행, "N곡 동안 시작" |
| `screens/light-02-running.png` | 4:480 | SCR-02 관측 | 레코드+톤암, 진행 패널(곡명), plus/pause/stop |
| `screens/light-02-estimated.png` | 4:1224 | SCR-02 D0 | "약 세 번째 곡" + "곡 정보 없이 평균 길이로 추정 중" |
| `screens/light-02-paused.png` | 4:1045 | SCR-03 | 톤암 거치, 알약 라벨, play |
| `screens/light-02-last-song.png` | 4:1123 | 끝 예고 | 마지막 칸 outline |
| `screens/light-03-result.png` | 4:555 | SCR-05 | 비교 카드, 곡 아이콘 열, 보정, 저장된 생각 |
| `screens/light-06-capture.png` | 4:1198 | SCR-08 | 여러 줄 필드, 취소, 저장하고 돌아가기 |
| `screens/light-07-correct-song-count.png` | 4:1211 | SCR-07 | 숫자 필드, 취소, 저장 |
| `screens/dark-0{1,2,3}-*.png` | 4:840·891·966 | Dark | Light와 같은 배치 |
| `screens/navy-01-home.png` | 4:634 | Navy | Light와 같은 배치 |

- Android에서 `light-02-running`(곡명 표시)은 **곡 세기 관측 중**(M-PLAYING)일 때 사용. 그 외는 `light-02-estimated`

## 와이어프레임 (User Flow 1:4 · `MT / Wireframes` 8:457)

| W | 노드 | SCR | 이동 |
|---|---|---|---|
| W01 집중 세션 | 8:463 | SCR-01 | [직접]→W05, [끝 정하지 않고 시작]→W10, [N곡 동안 시작]→W02 |
| W02 진행 중 | 8:493 | SCR-02 | [생각 메모]→W07, [세션 잠시 멈춤]→W06, [음악 바꾸기]→W09, [마치기]→W04 |
| W03 마무리할 시간 | 8:522 | 끝 예고 | [마치기]→W04. 자동 종료 없음 |
| W04 세션 끝 | 8:551 | SCR-05 | [곡 수 고치기]→W08, 다음 할 일 / 같은 일 이어서 |
| W05 시간 직접 정하기 | 8:589 | SCR-01 직접 입력 | 저장→W01 |
| W06 잠시 멈춤 | 8:608 | SCR-03 | [다시 시작]→W02, [여기서 마치기]→W04 |
| W07 생각 메모 | 8:625 | SCR-08 | 저장·취소→이전 화면 |
| W08 곡 수 고치기 | 8:642 | SCR-07 | 저장·취소→W04 |
| W09 음악 바꾸기 | 8:660 | SCR-06 | 선택하고 세션으로 |
| W10 끝 없는 세션 | 8:682 | 무한 모드 | 예고·배정 곡 수 없음 |

- 세션 흐름(`MT / 04 Session flow` 4:1327): 할 일 → 시간 배정 → 곡 환산 → 시작 → 진행 → 마지막 곡 → 결과 → 다음 행동, 분기 A(멈춤)·B(메모)
- **피그마에 없는 화면:** SCR-04 배정 도달, SCR-09 설정, SCR-11 곡 세기 안내, SCR-12 공유 수신, SCR-13 이 앱이 본 것 → 이 문서 토큰·컴포넌트로 구성

## 레코드·톤암

- 레코드 286dp, 홈 동심원 28개(3dp 간격, Canvas로 그림), 광택 PNG, sunset 라벨 100dp + 스핀들 8dp. 라벨 문구는 장식
- 톤암 회전축 (298, 18). 내림 30°, 거치 0°. 모션 650ms(내림)·550ms(들림) ease-in-out
- **Android 규칙 (SRS DEC-A12, iOS 결정과 다름):** 관측 중 재생·O0 재생에서만 톤암 내림 + 회전. 정지·곡 세기 꺼짐·미확인은 거치 + 정지
- 모션 줄이기(`ANIMATOR_DURATION_SCALE`=0) → 최종 위치 즉시, 정지 이미지

## 잠금화면·알림 (Lock Screen Rules 8:1240, Behavior 8:1261)

- Android = 표준 알림, 별도 채널. 잠금화면·OEM·폰트 설정 존중. **MediaStyle·가짜 재생 컨트롤 없음**
- 앱 소유 영역만 브랜드 서체, 시스템 알림은 시스템 서체
- 작업 제목·곡명 기본 숨김. 오렌지는 진행 포인트에만
- 알림 권한 ≠ 알림 접근(곡 세기) 권한. 관측 실패는 조용히 추정으로

## 에셋 (`assets/`)

| 파일 | Android 처리 |
|---|---|
| `icon-{gear,back,more,close,plus,pause,stop,note,chevron}.svg` | Vector Drawable로 변환 (24dp, note 22, chevron 16, 2dp stroke) |
| `dial-segment-1…7.svg` | 곡 수에 따라 개수가 달라짐 → Canvas로 그림 |
| `vinyl-sunset-label.svg`, `vinyl-spindle.svg`, `vinyl-groove-outer.svg` | Vector Drawable |
| `vinyl-sheen-282.png`, `result-extra-song.png` | `drawable-nodpi` 또는 밀도별 |
| `tonearm-*.svg` | Vector Drawable (팔·카트리지는 그라데이션 사각형 → Canvas) |
| `result-divider.svg` | Vector Drawable |

## 충돌 문구 (SRS 문구로 구현)

| 피그마·와이어프레임 | 문제 | 구현 문구 |
|---|---|---|
| 결과 "들은 양 · LISTENED · 9곡", W04 "약 9곡 분량이 지났어" | 추정 단정·반말 | D0 "약 N곡 분량", 관측 "N곡 지났어요" + 출처 라벨 (FR-A015) |
| "들은 곡 수 고치기", W08 "곡 수 고치기" | 추정 단정 | "곡 분량 고치기" 계열, 관측 중 진입점 숨김 (DEC-A11) |
| W06 "외부 음악은 그대로 재생돼요", Gui "음악 재생은 그대로예요" | 재생 상태 단정 | 곡 세기 꺼짐: "타이머만 멈췄어요. 음악은 음악 앱에서 조절해요" / 켜짐: 음악 패널 실제 상태 |
| 일시정지 알약 "세션을 잠시 멈췄어요", W06 "잠시 멈췄어" | SCR-03 문구 | "타이머만 쉬고 있어요" |
| W01·Design System "얼마나 할까?", W03 "마무리할 시간이야" | 반말 | 존댓말 "얼마나 하실까요?" 등 (SRS §1.1) |
| User Flow "외부 음악은 제어하지 않습니다", W01 "재생은 사용자가 직접" | ADR-0013로 개정 | 곡 세기 켜짐이면 사용자 버튼 재생/일시정지 + 끝나면 멈추기 허용 |
| W09 "기기 안의 음악" | A-P3 | A-P1에서는 메뉴 숨김 |
| 진행 "Slow Tide / LUMEN FIELD" | D0에서 곡명 금지 | 관측 중에만 제목 (FR-A018) |

## 추출하지 못한 항목 (MCP로 직접 확인 필요)

- Gui의 Navy 진행·결과 (4:686, 4:761)
- 컴포넌트 노드별 정확한 수치 (`get_design_context` 미호출. 이미지 기준)
- Android system template 노드 (Assets / Component 1:2 하위) 정확한 레이아웃
