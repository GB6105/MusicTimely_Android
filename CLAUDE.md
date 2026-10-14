# MusicTimely Android

## 명령 (`mise exec -- ./gradlew …`)
| 목적 | 명령 |
|---|---|
| JVM 테스트 전체 (단위·통합·기능) | `:domain:test :app:testDebugUnitTest` |
| 계층 하나 | `-PtestTier=unit\|integration\|feature` 추가 |
| 기기 테스트 | `:app:connectedDebugAndroidTest` (에뮬레이터 필요) |
| 앱 빌드 | `:app:assembleDebug` |
| 린트 | `:app:lintDebug` |

- JDK = mise temurin-21 (`mise.toml`). 셸 기본 `java` 없음 → 항상 `mise exec --`
- Android SDK: `/opt/homebrew/share/android-commandlinetools` (Homebrew cask). `local.properties` `sdk.dir`로 지정 (커밋 금지)
- 판정은 exit code. 파이프로 출력 자르지 않기

## 작업 전 Read
| 상황 | 경로 |
|---|---|
| 모든 코드 작성 (TDD 순서·테스트 계층) | `docs/testing.md` |
| 파일·패키지 위치 결정 | `docs/architecture.md` |
| 기능 구현·검증 | `docs/feature-checklist.md` 해당 F 섹션 |
| 요구사항 원문 | `docs/SRS-adhd-music-timer-android-v1.md` (우선) |
| "승계" 항목 원문 | `docs/SRS-adhd-music-timer-v1.md` |
| 제품 의도·열린 질문 | `docs/PRD-adhd-music-timer-android-v1.md` |
| UI 작업 | `docs/design/README.md` (토큰·컴포넌트·와이어프레임·충돌 문구) |

## TDD (필수)
- 테스트 먼저 → 올바른 이유로 실패 확인 → 최소 구현 → 리팩터 → 재실행
- 테스트 계층 = 클래스 접미사: `*Test` 단위 / `*IntegrationTest` 통합 / `*FeatureTest` 기능 / `*DeviceTest` 기기
- 대역은 손으로 쓴 fake. 시간은 `ClockPort` fake로만
- `@Ignore`·빈 테스트·항상 통과 assertion 금지

## 구조 (MVVM + 계층, 상세 `docs/architecture.md`)
| 위치 | 내용 |
|---|---|
| `:domain` `model/<기능>/` | 엔티티·순수 상태 머신·계산 |
| `:domain` `repository/` | Repository 인터페이스, `ClockPort` |
| `:domain` `usecase/<기능>/` | UseCase |
| `:app` `data/` | Repository 구현 + 데이터 소스 (`local`, `media`, `launcher`, `audio`, `notification`, `alarm`) |
| `:app` `service/`, `receiver/` | OS 진입점 → Repository로 위임 |
| `:app` `ui/<기능>/` | `XxxScreen` · `XxxViewModel` · `XxxUiState` |
| `:app` `di/` | Hilt 모듈 |
| `:testing` | 공유 fake (`FakeClock`, `Fake*Repository`, `Fake*Port`) |

- 의존 방향: UI → Domain ← Data. UI가 Data·플랫폼 API 직접 참조 금지
- 빈 패키지 미리 만들지 않기 (첫 테스트 작성 시 생성)
- 타이머 부수효과는 `TimerEffectHandler`를 `@IntoSet`로 추가 (UseCase·UI에서 플랫폼 직접 호출 금지)
- 관측(`data/media`)은 `SerialRunner` 단일 스레드에서만. 잠금 추가 금지
- 세션 read-modify-write는 `SessionRepository.withLock` 안에서

## 규칙
- `play` 전송 경로 = 사용자 음악 버튼 + 타이머 연동 재생(DEC-A16: 이 앱이 타이머와 함께 멈춘 같은 곡만) 2곳. `play`·`pause` 외 메서드 호출 금지
- 리스너에 `onNotificationPosted`/`onNotificationRemoved` 오버라이드 금지
- 곡 제목·아티스트·시그니처: 기본 메모리 전용. 예외(DEC-A18·A21) = 센 곡의 제목·아티스트·앨범 아트(앱 안 표시만, 공유 이미지 금지)만 `FilePlaylistStore`(noBackup)에 결과 닫기 전까지 최대 24시간. 그 외 저장·로그·예외 메시지·분석 금지
- 공유 이미지(DEC-A22): 곡명·아티스트만 `cacheDir/share/` PNG 1개, 결과 닫기·전체 삭제·앱 시작 시 삭제. 외부 전송은 사용자 탭 → `data/shareimage/ShareIntents.kt` 1곳만. 앨범 아트 금지
- `data/media`: 네트워크·로그 출력 금지
- 매니페스트 금지: `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, `QUERY_ALL_PACKAGES`, `READ_MEDIA_AUDIO`, `MEDIA_CONTENT_CONTROL`, 접근성 서비스, specialUse FGS
- `<queries>`: 지원 음악 앱·Play 스토어 + 예외 `com.instagram.android`(DEC-A22 공유)만
- 음악 레이어 실패가 타이머를 멈추면 안 됨 (MT-T7)
- 문구: 존댓말, `strings.xml` 리소스. 추정을 실제 청취로 단정 금지, 정시 알림 보장 문구 금지
- 터치 48dp, 대비 4.5:1, 글꼴 200%, 모션 줄이기 대응

## 빌드 함정
- `gradle.properties` `android.disallowKotlinSourceSets=false` 삭제 금지 (KSP ↔ AGP 9 내장 Kotlin 충돌)
- Robolectric 4.14.1은 SDK 35까지 → `app/src/test/resources/robolectric.properties` `sdk=35`
- `suspend` 함수에 `@Synchronized` 불가 → `synchronized` 블록 또는 `Mutex`
- 문자열 리소스에 실제 줄바꿈·맨 작은따옴표 금지 → `\n`, `\'`
- 적응형 아이콘은 `mipmap-anydpi-v26` 유지 (`-v26` 빼면 AAPT 실패, lint `ObsoleteSdkInt` 경고는 무시)

## 디자인
- Figma `0tVXNjTmVjGrpIjPcm977K` (Starter View 좌석 → MCP 호출 한도 작음). `docs/design/README.md` 먼저, MCP는 누락 항목만
- 시각(색·폰트·그림자·레이아웃)은 피그마, 문구·진실성은 SRS
- 폰트: `res/font/` Plex(400·500·700) + Montserrat variable, 숫자 `tnum`

## 체크리스트 갱신
- 항목 통과 시 `[x]` + `→ 증거: <테스트#메서드 | QA 기록>` 추가, 요약표 완료 수 갱신
- 스파이크 결과 없이 지원표 "스파이크" → "지원" 변경 금지
