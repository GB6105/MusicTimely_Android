# TDD·테스트 규칙

## TDD 순서 (모든 코드에 적용)

1. 체크리스트(`feature-checklist.md`) 항목 하나 선택
2. 인수 기준을 테스트로 먼저 작성 (가장 낮은 계층부터: 단위 → 통합 → 기능)
3. 실행 → **올바른 이유로 실패**하는지 확인 (컴파일 오류만으로 끝내지 말고, 최소 시그니처를 만든 뒤 assertion 실패 확인)
4. 테스트를 통과시키는 최소 구현
5. 리팩터 → 해당 모듈 전체 계층 재실행
6. 체크리스트 `[x]` + `→ 증거: <테스트 클래스#메서드>`

금지
- 실패를 확인하지 않은 테스트, 테스트 없는 프로덕션 코드
- `@Ignore`, 빈 테스트, 항상 통과하는 assertion, TODO로 남긴 분기
- 테스트를 통과시키려고 인수 기준을 바꾸는 일 (바꿔야 하면 SRS 근거와 함께 사용자 확인)

## 테스트 계층

| 계층 | 클래스 접미사 | 위치 | 실행 환경 | 대상 | 대역 |
|---|---|---|---|---|---|
| 단위 | `*Test` | `domain/src/test`, `app/src/test` | JVM | Model·상태 머신·UseCase·ViewModel | 전부 fake |
| 통합 | `*IntegrationTest` | `app/src/test` | JVM + Robolectric | Repository + 실제 저장소, 데이터 소스 + Android 프레임워크, ViewModel + 실제 UseCase | 시스템 경계(시계·다른 앱 미디어 세션)만 fake |
| 기능 | `*FeatureTest` | `app/src/test` | JVM + Robolectric + Compose UI | 체크리스트 F 항목·QA 시나리오를 화면에서 끝까지 | 시스템 경계만 fake |
| 기기 | `*DeviceTest` | `app/src/androidTest` | 에뮬레이터·실기기 | Robolectric로 재현 못하는 플랫폼 동작 (알림 접근 바인딩, 실제 음악 앱 세션) | 없음 |

- 실기기 수동 QA(체크리스트 D)는 자동 테스트로 대체하지 않음
- 테스트 패키지 = 대상 패키지 (같은 패키지에 둠). 기능별로 자연히 묶임
- Robolectric SDK = 35, 화면 `w411dp-h891dp-xxhdpi` (`app/src/test/resources/robolectric.properties`). Robolectric이 SDK 36을 지원하면 올림
- AGP는 JVM 모듈의 testFixtures를 해석하지 못함 → 공유 fake는 `:testing` 모듈로
- 글자 줄 수·말줄임 검증: 클래스에 `@GraphicsMode(GraphicsMode.Mode.NATIVE)` + `SemanticsActions.GetTextLayoutResult` (예: `SessionTitleFeatureTest`)
- 픽셀 검증: `captureToImage()`는 Robolectric에서 시간 초과 → 그리기를 `DrawScope` 함수로 분리하고 `CanvasDrawScope`로 `ImageBitmap`에 그려 검사 (예: `TurntableDrawingFeatureTest`)
- Robolectric + `AlertDialog` 안 `TextField` 입력 → `ShadowLineBreaker` 무한 등록으로 OOM. 입력은 화면 안 인라인으로 (예: 곡 보정)

## 실행

| 목적 | 명령 (`mise exec -- ./gradlew …`) |
|---|---|
| 전체 (JVM 3계층) | `:domain:test :app:testDebugUnitTest` |
| 단위만 | `-PtestTier=unit :domain:test :app:testDebugUnitTest` |
| 통합만 | `-PtestTier=integration :app:testDebugUnitTest` |
| 기능만 | `-PtestTier=feature :app:testDebugUnitTest` |
| 기능 패키지 하나 | `:app:testDebugUnitTest --tests 'com.gb6105.musictimely.ui.session.*'` |
| 기기 | `:app:connectedDebugAndroidTest` (에뮬레이터 필요) |

### 에뮬레이터 (기기 계층)
| 단계 | 명령 |
|---|---|
| AVD (최초 1회) | `avdmanager create avd -n mt35 -k "system-images;android-35;google_apis_playstore;arm64-v8a" -d pixel_7` |
| 부팅 (창 없음) | `$SDK/emulator/emulator -avd mt35 -no-window -no-audio -no-boot-anim &` → `adb wait-for-device` + `getprop sys.boot_completed`=1 |
| 실행 | `mise exec -- ./gradlew :app:connectedDebugAndroidTest` |
| 종료 | `adb emu kill` |

- `$SDK` = `/opt/homebrew/share/android-commandlinetools`. 권한·알림 접근은 테스트가 `uiAutomation.executeShellCommand`로 부여·회수 (`DeviceShell.kt`)
- 앱의 실제 싱글턴은 `appGraph` (`AppGraph.kt`) ← `src/debug/.../di/DeviceTestEntryPoint.kt` (debug 빌드 전용). OS 비동기 작업은 `eventually { }`로 대기
- `dumpsys` 판정: 대기 알람 = `ELAPSED_WAKEUP #n: Alarm{… 패키지}` 줄만 (제거 기록 줄 제외), FGS = `isForeground=true foregroundId=1 types=0x00000002`
- 클래스 하나만: `-Pandroid.testInstrumentationRunnerArguments.class=<FQCN>[#method]`
- `connectedDebugAndroidTest`는 끝나면 앱을 제거 → 알림 접근(곡 세기) 허용도 사라짐. 수동 확인 전 `adb shell cmd notification allow_listener com.gb6105.musictimely/com.gb6105.musictimely.service.MediaAccessListener`

- 계층 필터는 루트 `build.gradle.kts`의 `testTier` 속성. 판정은 exit code

## 작성 규칙

| 항목 | 규칙 |
|---|---|
| 라이브러리 | JUnit4 + Truth(`assertThat`) + Turbine(Flow) + kotlinx-coroutines-test(`runTest`) |
| 대역 | mock 라이브러리 대신 손으로 쓴 fake (`FakeClock`, `FakeSessionRepository` …) |
| fake 위치 | 모듈 간 공유 fake는 `:testing` 모듈 (`com.gb6105.musictimely.testing`). 한 테스트에서만 쓰는 fake는 그 테스트 파일 안 |
| 시간 | `ClockPort` fake로만 조작. `Thread.sleep`·실제 시계 금지 |
| 코루틴 | `StandardTestDispatcher`, ViewModel은 `Dispatchers.setMain` 규칙 사용 |
| 이름 | JVM 테스트: 백틱 문장 (`` `start twice keeps one session` ``). 기기 테스트: camelCase (dex 제약) |
| 한 테스트 | 동작 하나, Given-When-Then 순서 |
| 근거 표기 | 테스트 클래스 KDoc 또는 메서드 이름 옆 주석에 요구사항 ID (`FR-A008`, `QA-05`) |

## 기능별 주 계층

| F | 단위 | 통합 | 기능 | 기기 |
|---|---|---|---|---|
| F01 타이머 | 상태 머신·시간식·UseCase | — | 시작·정지·연장·마침 흐름 | — |
| F02 저장·복원 | 체크포인트 모델 | 원자 저장·마이그레이션·백업 제외 | 강제 종료 후 복원 화면 | 재부팅 |
| F03 배정·D0 | 계산식·예고 규칙 | — | 배정 화면 번역·직접 입력 | — |
| F04·F17 UI·디자인 | 토큰 값 | — | 화면·컴포넌트·이동 | 접근성 |
| F05 외부 음악 | URL 추출·허용 목록 | 공유 인텐트 수신·열기 폴백 | 등록·열기 흐름 | 실제 앱 열기 |
| F06 곡 세기 | 집계·관측 상태 머신 | 세션 연결·권한 해제 | 패널 상태·이 앱이 본 것 | 리스너 바인딩·실제 음악 앱 |
| F07 재생 버튼·끝나면 멈추기 | 상태 머신·디바운스 | 명령 전송·확인 | 대기 문구·버튼 | 실제 음악 앱 명령 |
| F08 알림·알람 | 문구·재조정 키 | 채널·알림 내용·알람 예약 | 권한 요청 흐름 | 잠금화면·Doze |
| F09 내장 소리 | 페이드·볼륨 규칙 | 플레이어·포커스 | 소스 전환 | 헤드폰·통화 |
| F10 결과·메모·설정 | 결과 계산·TTL | 설정 저장·삭제 | 결과·메모·설정 화면 | — |
| F11 정적 검사 | lint 규칙 테스트 | — | — | — |
| F12 분석 | 허용 필드 | sink OFF/ON | — | — |
