# 기능별 확인 절차·체크리스트 (MT-AOS v1)

- 기준: `SRS-adhd-music-timer-android-v1.md`(MT-SRS-AND-001 v1.0), `PRD-adhd-music-timer-android-v1.md`(v1.0)
- 승계 원문: `SRS-adhd-music-timer-v1.md`(MT-SRS-001 v1.0). "승계" 항목은 그 인수 기준을 풀어 적음
- 현재 상태: **기능 항목 전부 미완료** (완료는 F00 개발 환경 1건)

## 표기

| 표기 | 의미 |
|---|---|
| `[ ]` | 미완료 |
| `[x]` | 완료. 확인 절차 통과 + 증거(테스트 이름·QA 기록 경로) 기재 |
| U | 단위 테스트 `*Test` (JVM, fake) |
| I | 통합 테스트 `*IntegrationTest` (JVM + Robolectric). Robolectric로 재현 불가하면 기기 테스트 `*DeviceTest` |
| UI | 기능 테스트 `*FeatureTest` (Compose UI + Robolectric) |
| S | 정적 검사 (CI 규칙, F11) |
| D | 실기기 수동 QA (§1.3 기기·OS 매트릭스, 영상 기록) |
| R | 문서·결정 검토 |
| `제거됨 (DEC-xx)` | 사용자 결정으로 범위에서 뺀 항목. 원문 줄은 남기고 체크박스 대신 표기. 항목·완료 수에서 제외 |

## 완료 판정 규칙

1. 테스트를 먼저 쓰고 실패를 확인한 뒤 구현 (TDD, `docs/testing.md`). 항목의 확인 방법(U/I/UI/S/D/R)을 모두 통과해야 `[x]`
2. 증거를 항목 끝에 `→ 증거: <테스트 클래스#메서드 | QA 기록 경로>`로 추가
3. 기능(F) 완료 = 하위 항목 전부 `[x]` + 해당 QA 전부 `[x]`
4. 판정은 exit code 기준. 파이프로 자른 출력으로 판정 금지
5. 스파이크 결과 없이 "스파이크" 항목을 "지원"으로 바꾸지 않음 (SRS §15.3)

## 진행 요약

| ID | 기능 | ENG | 항목 | 완료 | 상태 |
|---|---|---|---|---|---|
| F00 | 선행 조건·결정 | ENG-A00 | 13 | 1 | 미완료 |
| F01 | 타이머 도메인 | ENG-A01 | 20 | 12 | 미완료 |
| F02 | 저장·복원 | ENG-A02 | 15 | 10 | 미완료 |
| F03 | 배정·D0 번역 | ENG-A03 | 23 | 20 | 미완료 |
| F04 | UI·접근성 | ENG-A04 | 27 | 14 | 미완료 |
| F05 | 외부 음악 등록·열기 | ENG-A05 | 13 | 9 | 미완료 |
| F06 | 곡 세기(관측) | ENG-A06 | 40 | 24 | 미완료 |
| F07 | 재생 버튼·끝나면 멈추기 | ENG-A07 | 28 | 18 | 미완료 |
| F08 | 알림·알람 | ENG-A08 | 17 | 10 | 미완료 |
| F09 | 내장 소리 | ENG-A09 | 12 | 2 | 미완료 |
| F10 | 결과·메모·설정·삭제 | ENG-A10 | 12 | 7 | 미완료 |
| F11 | 정적 검사·CI | ENG-A11 | 6 | 6 | 완료 |
| F12 | 분석 이벤트 | — | 5 | 3 | 미완료 |
| F13 | 비기능 | — | 14 | 1 | 미완료 |
| F14 | 로컬 파일 (A-P3, 후속) | — | 6 | 0 | 미완료 |
| F15 | Play 제출 | ENG-A12 | 7 | 0 | 미완료 |
| F16 | 릴리스 게이트 | ENG-A13 | 9 | 0 | 미완료 |
| F17 | 디자인 시스템·컴포넌트·에셋·와이어프레임 | ENG-A04 | 29 | 11 | 미완료 |
| F18 | 세션 플레이리스트·공유 이미지 | — | 11 | 7 | 미완료 |

## 공통 확인 명령

| 대상 | 명령 |
|---|---|
| JVM 3계층 전체 | `mise exec -- ./gradlew :domain:test :app:testDebugUnitTest` |
| 계층 하나 | 위 명령 + `-PtestTier=unit\|integration\|feature` |
| 기기 | `mise exec -- ./gradlew :app:connectedDebugAndroidTest` (에뮬레이터 필요) |
| 린트 | `mise exec -- ./gradlew :app:lintDebug` |
| 정적 검사 | F11에서 확정 |

---

## F00. 선행 조건·결정

절차: 결정은 승인자·날짜를 이 문서 또는 ADR에 기록. 스파이크는 서비스별 결과표(PRD 8장)를 갱신.

- [x] 개발 환경: Android SDK 설치 + `local.properties` `sdk.dir` 설정, `:app:assembleDebug` 성공 (R) → 증거: 2026-10-10 Homebrew cmdline-tools(`/opt/homebrew/share/android-commandlinetools`), platform 36·build-tools 36.0.0, assembleDebug exit 0
- [ ] ENG-A00 A-P0 스파이크: 서비스 6종(유튜브뮤직·스포티파이·멜론·지니·FLO·애플뮤직)별 (1) 링크·앱 열기 (2) 곡 전환 메타데이터 (3) `play`/`pause` 수용 (4) 곡 길이 제공 (5) 화면 꺼짐 경계 정지 지연 (6) 제조사 차이 (D)
- [ ] 스파이크 결과로 PRD 8장 지원표 확정. 국내 주요 앱 다수가 곡 전환 미발행이면 ADR-0014 재검토 기록 (R)
- [ ] 국내 앱(멜론·지니·FLO) 공유 링크 호스트 실측 → §7.5 허용 목록 확정 (D)
- [ ] §7.1 `<queries>` 패키지명 Play 스토어 재확인 (R)
- [ ] 승계 DEC-01·02·04·07 승인 기록 (R)
- [ ] DEC-A01(권한 안내 시점)·DEC-A05(끝나면 멈추기 상한 6분·문구)·DEC-A06(비정확 알람) 승인 기록 (R)
- [ ] minSdk 29 확정, targetSdk = 출시 시점 Play 신규 앱 요구 수준 확인 (R)
- [ ] 대표 기기 고정: Galaxy(One UI) 2종 + Pixel 1종, OS 10·13·14·15·16·17 (R)
- [ ] 열린 질문 1 무한 모드 포함 여부 결정 (R)
- [ ] 열린 질문 2 V1 테마(Light/Navy/Dark)·폰트 확정 (R)
- [ ] 열린 질문 3 잠금화면 범위 = 진행 알림 하나 확인 (R)
- [ ] 참조 ADR-0013·0014·0015, 스트리밍 연동 howto 문서 확보 (`docs/`에 없음) (R)

---

## F01. 타이머 도메인 (ENG-A01)

위치: domain `model/timer`·`model/session`·`usecase/session`, `repository/ClockPort` → ui `session/`. 선행: F00 DEC 승인.

확인 절차
1. fake clock(단조·벽시계 분리 조작)을 주입해 상태 전이를 단위 테스트로 고정
2. 각 전이의 부수효과는 포트(알람·알림·관측·외부 명령) 가짜 구현에 기록된 호출 수로 검증
3. 외부 명령 포트 호출 수가 타이머 조작 전 구간에서 0인지 확인

체크리스트
- [x] 상태 6종 READY/RUNNING/PAUSED/LIMIT_REACHED/ENDED/RECOVERY_REQUIRED와 SRS v1 §5.1 전이표 구현 (U) → 증거: TimerStateMachineTest (전이 전체)
- [x] 시간식 `E = accumulatedActiveMs + max(0, mono - anchor)`, 유한이면 `min(E, target)`, 잔여 `max(0, target - E)` (U) → 증거: TimerStateMachineTest#elapsed follows accumulated plus monotonic delta
- [x] FR-A007 시작 1탭·멱등: 연속 10회 START → sessionId 1개·알람 1개. 권한·외부 앱·오디오와 무관하게 시작 (U) → 증거: TimerStateMachineTest#ten rapid starts…, SessionUseCasesTest
- [x] FR-A008 60초 진행 → 30초 정지 → 60초 진행 = 120초. 외부 명령 0회 (곡 세기 켜짐 포함) (U) → 증거: TimerStateMachineTest#60s run, 30s pause, 60s run…, SessionViewModelTest#pause freezes time…
- [x] FR-A009 수동 종료: 확인 모달 없이 1탭 결과, 종료 이벤트 1건, 끝나면 멈추기 대기 취소, 외부 명령 0 (U, UI) → 증거: TimerStateMachineTest#finish before the target…, SessionFeatureTest#finishing goes to the result
- [x] FR-A010 목표 도달 → LIMIT_REACHED, 경과 고정, 진입 시 끝나면 멈추기 평가 호출 (U) → 증거: TimerStateMachineTest#reaching the target clamps elapsed…
- [x] FR-A011 +5/+10/+15분 연장: 최초 배정 보존, 목표만 증가, targetRevision +1, 대기 시간 제외, 끝나면 멈추기 취소, `play` 0 (U) → 증거: TimerStateMachineTest#extend keeps the initial allocation…, #waiting at the limit is excluded…
- [x] FR-A013 활성 세션 1개: 새 진입은 기존 세션으로 이동, 덮어쓰기 없음 (U) → 증거: SessionUseCasesTest#opening while a session is live returns it…
- [x] FR-A005 무한 모드: 목표 null, 잔여·예고·끝나면 멈추기 없음, END_REASON user_ended (U) → 증거: TimerStateMachineTest#infinite mode has no target…
- [x] REACH_LIMIT는 화면 tick·종료 알람·관측 콜백 중 먼저 감지한 곳에서 1회 (commandId 멱등) (U) → 증거: TimerStateMachineTest#tick past the target reaches the limit once, #a repeated command id is applied once
- [x] 같은 tick에 도달·종료가 겹치면 목표로 정규화 후 FINISH 1회 (U) → 증거: TimerStateMachineTest#finish on the same tick as the target…
- [x] END_REASON completed/user_ended/abandoned 기록 (U) → 증거: TimerStateMachineTest#finish…, #abandoning recovery ends as abandoned

QA
- [ ] QA-01 최초 실행·계정 없음·알림 거절 → 25분 D0 바로 시작, 강요 없음 (D)
- [ ] QA-A01 첫 실행, 권한 전부 거절 → 25분 D0 시작·종료 정상, 재요청 0 (D)
- [ ] QA-04 시작 연속 10회 탭 → 활성 세션·알림 예약 1개 (U, D)
- [ ] QA-05 60·30·60초 → 120초, 음악 제어 명령 0 (곡 세기 켜짐 포함) (U, D)
- [ ] QA-09 도달 후 2분 대기 → +5분 → 3분 후 마침: 최초 25분·활성 28분, 대기 제외, 연장 표시 (U, D)
- [ ] QA-10 무한 20분: 끝 알림·잔여·초과율 없음, 사용자 종료 가능 (D)
- [ ] QA-11 시계 +1시간·타임존 변경 → 같은 부팅에서 경과 불변 (U, D)
- [ ] QA-40 LIMIT_REACHED에서 자체 소리·외부 소스 각각 연장 → 타이머만 재개, 자체 재생은 별도 탭 (D)

---

## F02. 저장·복원 (ENG-A02)

위치: domain `model/session`(체크포인트) → data `local/`·`repository/`. 선행: 데이터 정책 리뷰.

확인 절차
1. 원자 저장(임시 파일 → rename)과 스키마 마이그레이션을 장애 fixture로 테스트
2. 프로세스 강제 종료(`adb shell am kill`) 후 재실행으로 복원 확인
3. 백업 제외는 `adb shell bmgr` 백업·복원으로 체크포인트가 빠지는지 확인

체크리스트
- [x] FR-A012 백그라운드·프로세스 종료 복원: UI tick 없이 시간 복원, 유한 세션은 목표에서 clamp (U, I) → 증거: RestorePolicyTest, RestoreSessionUseCaseTest, FileCheckpointStoreIntegrationTest, AppFlowFeatureTest#a session killed before the target reopens at the limit
- [x] 단조 시계 `SystemClock.elapsedRealtime()` 사용 (§5.4) (U) → 증거: AndroidClockIntegrationTest#monotonic time follows elapsedRealtime
- [x] `Settings.Global.BOOT_COUNT` 체크포인트 저장, 불일치 시 RECOVERY_REQUIRED (U, I) → 증거: RestorePolicyTest#a different boot count…, AndroidClockIntegrationTest#boot count comes from Settings Global
- [x] 알람 지연을 측정 시간에 더하지 않음 (U) → 증거: RestorePolicyTest#a running session killed before the target…
- [x] 활성 체크포인트 원자 저장, 7일 초과 시 자동 복원 금지 (U) → 증거: FileCheckpointStore(AtomicFile), RestorePolicyTest#a checkpoint older than 7 days…
- [x] 체크포인트 백업 제외 (`dataExtractionRules`) (I) → 증거: FileCheckpointStoreIntegrationTest#lives in the no-backup directory… (dataExtractionRules 대신 noBackupFilesDir 사용)
- [x] AndroidCheckpointExt 필드(bootCount, stopAtEndSnapshot, stopAtEndOutcome, observedFromActiveMs, countedItemsAtCheckpoint) 저장. 곡 제목·아티스트·시그니처 0 (U) → 증거: FileCheckpointStoreIntegrationTest#reads back exactly…, #the stored file has no song fields
- [x] 복원 후 곡 정보 없음 → confidence mixed/unknown, 거짓 보간 0 (U) → 증거: RestorePolicyTest#restored observation data is only the integer count…
- [x] CHECKPOINT_WRITE_FAILED: "앱 종료 후 복원되지 않을 수 있어요" 1회, 메모리 진행 유지 (U, UI) → 증거: DefaultSessionRepositoryTest#a failed write…, SessionFeatureTest#a failed checkpoint write is told once
- [x] CORRUPT_CHECKPOINT·CLOCK_DISCONTINUITY → 복구/폐기 선택, 임의 실행 0 (U, UI) → 증거: RestoreSessionUseCaseTest#a corrupt checkpoint…, RestorePolicyTest#a different boot count…, #a recovery choice left unanswered…, AppFlowFeatureTest#another boot asks before resuming

QA
- [ ] QA-06 배경·잠금 10분 후 복귀 → tick 손실 없이 복원 (D)
- [ ] QA-07 PAUSED에서 프로세스 종료·재시작 → PAUSED 유지, 시간 추가 없음 (I, D)
- [ ] QA-08 목표 전 강제 종료 → 목표 후 재실행 → 목표 시각에서 LIMIT_REACHED (I, D)
- [ ] QA-12 재부팅·손상 체크포인트 → 추정 위장 없이 복구 선택 (D)
- [ ] QA-A32 관측 중 프로세스 강제 종료 후 복귀 → 시간 복원, 곡 정보 없음, mixed (D)

---

## F03. 배정·D0 번역 (ENG-A03)

위치: domain `model/translation`·`model/allocation` → ui `allocation/`·`correction/`. 선행: DEC-10.

확인 절차
1. 계산식(N·R·U·I·칩)을 표 기반 단위 테스트로 고정 (SRS v1 §6.1 예시 표 전부)
2. 문구는 리소스 키 단위로 UI 테스트에서 확인
3. 오프라인(비행기 모드)에서 번역 갱신 확인

체크리스트
- [x] FR-A001 작업 제목 선택: 공백이면 "지금 하는 일", 최대 100자, 두 줄 초과 말줄임, 접근성 전체 낭독 (U, UI) → 증거: AllocationInputTest(TaskTitle 공백→null·100자소 절단), SessionFeatureTest#no title shows the default task label, SessionTitleFeatureTest#a long title stops at two lines with an ellipsis (@GraphicsMode NATIVE, 변이 검증: maxLines 4로 바꾸면 실패), #screen readers get the whole title
- [x] FR-A002 10/25/40분 + 직접 입력: 25분 최초 선택, 1~240 정수 외 필드 오류·시작 불가, 소요 예측 질문 없음 (U, UI) → 증거: AllocationInputTest, AllocationViewModelTest, AllocationFeatureTest
- [x] FR-A003 시간 선택 즉시 D0 번역: 25분·240초 → "25분이면 약 7곡이에요", 오프라인 갱신, 곡 세기와 무관 (U, UI) → 증거: SongTranslationTest, AllocationViewModelTest, AllocationFeatureTest#shows 25 minutes as about 7 songs…
- [x] FR-A004 평균 곡 길이 기본 240초(60~1,800초), 세션 시작 시 스냅샷, 진행 세션 소급 없음 (U) → 증거: AllocationInputTest#average song length…, AllocationSnapshotTest
- [x] FR-A006 이전 작업 시간 제안: opt-in, 같은 taskId 마지막 시간만 "지난번 세션 30분, 이번에도?", 유사도 추론 없음 (U) → 증거: TaskHistoryTest
- [x] D0 식: N = max(1, ceil(T/d)), R = max(0, ceil((G-E)/d)), U = E/d (소수 첫째 자리, 정수면 생략) (U) → 증거: SongTranslationTest + FormatTest#whole numbers omit the decimal (변이 검증: 소수 강제 시 실패 확인)
- [x] 예시: T=1,500·d=240 → 7곡, 목표 정확히 25분 / E=564 → 4곡 남음·2.4곡 / T=120 → 시작 직후 예고 없음 / E=G → 잔여 0 (U) → 증거: SongTranslationTest, LastSongCueTest#allocation shorter than one song never cues at start
- [x] 목표를 N×d로 바꾸지 않음 (U) → 증거: AllocationViewModelTest#start opens a session… (목표 25분 그대로)
- [x] FR-A014 잔여 "약 N곡 분량 남았어요" + 실제 남은 시간. 관측 중에도 잔여는 시간 기준 (U, UI) → 증거: SessionViewModelTest#running shows remaining songs…, SessionFeatureTest#running shows the remaining song amount…
- [x] FR-A015 출처 표기: D0 "약 N곡 분량" / 관측 "N곡 지났어요" / 혼합 "N곡 지났어요 (곡 세기 시작 후)". 실측·추정 합산 0 (U, UI) → 증거: ResultUiStateTest#an observed count is labelled observed…, SessionFeatureTest#observed music shows… — **DEC-A20 (2026-10-13)**: 결과 화면은 곡 수 문장을 표시하지 않음(목록·일부 관측 안내만). 출처 표기는 세션 화면에서 유지
- [x] FR-A016 곡 보정: 정수 1~999, 표시 기준점만 변경(anchorIndex·anchorElapsed), 경과·목표·알림 동일, "곡 표시만 맞췄어요. 정한 시간은 그대로예요.", 관측 중 진입점 숨김(DEC-A11) (U, UI) → 증거: SongCorrectionTest + SessionViewModelTest#correcting the current song moves only the song display, #a correction outside 1 to 999 is rejected, #while songs are observed the correction entry is hidden (DEC-A11), SessionFeatureTest#the current song can be corrected in session without touching time — **DEC-A20 (2026-10-13)**: 결과 화면 진입점(곡 분량 고치기) 제거, 세션 화면 보정만 유지
- [x] FR-A017 마지막 곡 예고: 관측+길이 앎 → `(G-E) ≤ (L-p)`이면 "이 곡이 끝나면 정한 시간이에요" / 그 외 `(G-E) ≤ d`이면 "대략 한 곡 분량 남았어요". targetRevision당 1회, 도달 후 늦은 예고 폐기 (U) → 증거: LastSongCueTest, SessionViewModelTest#the last song cue…, SessionFeatureTest#about one song left…
- [ ] FR-A019 한 곡 반복: 관측 중이면 DEC-A04 실제 반복 수, 아니면 "약 N바퀴 분량". 반복 곡 길이 입력 60~1,800초(분:초), 공백이면 평균 (U, UI) → 부분 증거: AllocationViewModelTest#single-loop mode translates…, SessionFeatureTest#a single-loop session counts loops…, SettingsViewModelTest#single-loop mode and its optional loop length… + SettingsFeatureTest#single-loop mode and its loop length…, SessionNotifierIntegrationTest#a single-loop session counts loops… (남음: 관측 중 DEC-A04 실제 반복 수 표시 — DEC-A04 해석 보류)
- [x] FR-A020 곡 표시 끄기: 숫자 타이머·세션 기능 유지, 칩·제목 숨김, 재생 버튼 유지, 일반 끝 예고 (UI) → 증거: MusicPanelStateTest#song display off hides the title…, SettingsViewModelTest#song display can be turned off + SessionFeatureTest#song display off keeps the numeric timer…, #song display off gives a plain end cue… (일반 예고 문구 '곧 정한 시간이에요'는 SRS 원문 부재로 임시 → 보류)
- [x] FR-A021 큰 곡 수 칩 그룹화: 시각 칩 최대 8개, 초과 시 "약 N곡 분량 남음" 중심, 접근성 총량 제공 (UI) → 증거: ChipLayoutTest, SessionFeatureTest#up to eight songs show chips…, #seven songs are drawn as individual chips

QA
- [ ] QA-02 25분·평균 4분 → 약 7곡, 목표 25분 유지 (U, D)
- [x] QA-03 시간 0·음수·소수·241·문자 → 필드 오류, 시작 불가 (U, UI) → 증거: AllocationInputTest#custom minutes accept…, AllocationFeatureTest#an out of range custom time…
- [ ] QA-13 D0에서 외부 음악 끄기 → 타이머·추정 계속, 가짜 곡명·EQ 없음 (D)
- [x] QA-14 09:24 시점·25분 → 약 4곡 남음, 경과 564초 (U) → 증거: SongTranslationTest#remaining songs rounds up…, SessionFeatureTest#running shows…
- [x] QA-15 곡 인덱스 보정 → 경과·목표·예고 시각 동일 (U) → 증거: SongCorrectionTest
- [x] QA-16 잔여 4분 반복 진입·복원 → 같은 targetRevision당 예고 1회 (U, I) → 증거: SessionViewModelTest#the cue is not repeated after re-entry or restore inside the last song, #after an extension the new target gets its own cue (변이 검증: 창 안 재무장 허용 시 실패 확인)
- [x] QA-17 2분 배정(평균 4분) → 시작 시 방해 예고 없음, 2분에 도달 (U) → 증거: LastSongCueTest#allocation shorter than one song never cues at start
- [x] QA-41 반복 길이 03:42 / 공백 / 범위 초과 → 222초 / 평균 / 오류, 진행 세션 소급 없음 (U, UI) → 증거: AllocationInputTest#loop length parses…

---

## F04. UI·접근성 (ENG-A04)

위치: ui `theme/`·`component/`·`navigation/`·`<기능>/`. 선행: 테마·폰트 결정(F00), F17 디자인 시스템, Figma `Music Timely`(`0tVXNjTmVjGrpIjPcm977K`).
디자인 사양: `docs/design/README.md` (토큰·컴포넌트·화면·와이어프레임·충돌 문구).
UI가 MediaSessionManager·AlarmManager를 직접 호출하지 않음.

확인 절차
1. 화면별 Compose UI 테스트(상태 주입) + Figma 대조 스크린샷
2. TalkBack·글꼴 200%·모션 줄이기·대비를 실기기로 검수

화면
- [x] SCR-01 배정: 작업 제목, 시간 칩, 직접 입력, 분→곡 번역, 시작, 무한 모드 보조 링크 (UI) → 증거: AllocationFeatureTest
- [x] SCR-02 진행: 제목, 레코드+톤암, 잔여 분량, 실제 경과, 칩, 음악 패널, 일시정지, 마침, 나중에. 음악 상태가 화면 이탈을 강제하지 않음 (UI) → 증거: SessionFeatureTest
- [x] SCR-03 일시정지: "타이머만 쉬고 있어요", 고정 경과, 이어서, 마침, 음악 패널 유지. 음악 명령 0 (UI) → 증거: SessionFeatureTest#pausing rests the timer only…, #pausing while observed does not claim…
- [x] SCR-04 배정 도달: "정한 시간이 됐어요", 경과, 마치기, +5/+10/+15분, 대기 시 "지금 곡이 끝나면 음악도 멈춰요" [지금 멈추기] [계속 듣기] (UI) → 증거: SessionFeatureTest#reaching the target…, #waiting to stop at the end offers…
- [x] SCR-05 결과: ~~배정·세션 시간, 곡 수·출처 라벨~~, 다시 시작(새 sessionId), ~~선택적 체감 시간~~ (UI) — **DEC-A19·DEC-A20으로 개정 (2026-10-13)**: 시안 A만 = 닫기·이번 플레이리스트·총 걸린 시간·곡 목록·메모·[완료]·[인스타로 공유하기](비활성). 배정 대비·곡 수 라벨·체감 시간·"같은 일 이어서 하기" 제거 → 증거: ResultFeatureTest#완료 closes the result…, #the close button leaves the result like 완료 (W04), #the result has no comparison, summary, icons, caption or task title (DEC-A20), #the result offers no song correction, felt-time question or same-task continue (DEC-A20), AppFlowFeatureTest#first launch goes straight to allocation, start, finish, result and restart (QA-01)
- [x] SCR-06 음악 선택 시트: 저장 소스, [음악 앱 추가], [링크 붙여넣기], 내장 노이즈, 음악 없음. 시작 전 강제 없음 (UI) → 증거: MusicSheetFeatureTest
- 제거됨 (DEC-A20): ~~SCR-07 곡 보정 시트 (관측 중 진입점 숨김) (UI)~~ — 결과 화면(W04→W08) 진입점 삭제 (2026-10-13). 세션 화면 곡 보정은 F03 FR-A016에서 유지
- [x] SCR-08 생각 메모 (UI) → 증거: MemoFeatureTest, AppFlowFeatureTest#a thought memo taken during the session…
- [ ] SCR-09 설정: 평균 곡 길이, 곡 표시, 볼륨·페이드, 과제 제안, 저장 음악 앱 관리, 곡 세기, 끝나면 멈추기, 알림, 데이터 삭제 (UI) → 부분 증거: SettingsFeatureTest + SettingsFeatureTest#built-in sound volume…, #single-loop mode and its loop length… ⟵ 남음: 과제 제안 항목(PRD 원문 부재로 의미 확인 필요)
- [ ] SCR-11 곡 세기 안내: PRD 5.4 문구, [켜기] [나중에], 시스템 경고 문구를 먼저 설명 (UI) → 부분 증거: SettingsFeatureTest#turning counting on shows the prompt… (문구는 PRD 5.4 원문 대조 필요 → 보류) ⟵ 감사(10-10) 미충족: PRD 5.4 문구 대조(R) 미완
- [x] SCR-12 공유 수신 확인: 아이콘·이름, 이름 입력(기본 "유튜브뮤직 플레이리스트"), [저장] [취소], 미지원 링크 문구 (UI) → 증거: ShareFeatureTest
- [x] SCR-13 이 앱이 본 것: 연결 앱, 현재 곡(제목·아티스트), 센 곡 수, 보낸 명령(시각·종류). 없으면 "지금은 아무것도 보고 있지 않아요" (UI) → 증거: SeenFeatureTest

음악 패널 (§3.1)
- [x] M-EMPTY "음악 앱을 골라 주세요" [고르기] / M-EXT-OFF "〈앱〉 · 평균 4분 기준" + "곡 세기 켜기" 링크 [열기] / M-EXT-WAIT [열기] (UI) → 증거: MusicPanelStateTest#no saved source…, #counting off shows…, #counting on without a target…
- [x] M-PLAYING "지금 〈제목〉" [일시정지] / M-PAUSED "멈춤 · 〈제목〉" [재생] (응답 없음이면 [열기]) / M-HANDOFF "〈앱〉에서 재생 중 · 지금 〈제목〉" (UI) → 증거: MusicPanelStateTest#observing playing…, #observing paused…, #a handoff names…
- [x] M-NOISE "핑크 노이즈" 앱 자체 [재생]/[정지] / M-NONE "음악 없이" [바꾸기] (UI) → 증거: MusicPanelStateTest#a chosen built-in noise…, #choosing no music…, SessionFeatureTest#built-in noise starts only from the user's tap…, #no music shows music-free
- [x] 진실성: 관측하지 않은 곡명·아트·재생 위치 0. "타이머만 멈췄어요. 음악은 음악 앱에서 조절해요"는 곡 세기 꺼짐에서만 (UI) → 증거: MusicPanelStateTest#the D0 pause copy appears only while counting is off, SessionFeatureTest#pausing while observed does not claim…

레코드·톤암·접근성
- [ ] DEC-A12 관측 재생·O0 재생에서만 톤암 내림(0°)+회전, 그 외 들림(30°)+정지. 650ms/550ms (UI, D)
- [ ] 모션 줄이기(`ANIMATOR_DURATION_SCALE`=0) → 최종 위치 즉시·정지 이미지 (UI, D)
- [ ] 폰트 IBM Plex Sans KR + Montserrat, 타이머 숫자 `tnum` 실제 렌더링 확인 (D)
- [ ] 터치 영역 ≥48dp, 대비 본문 4.5:1·큰 텍스트·비텍스트 3:1 (UI, D)
- [ ] 글꼴 200%에서 핵심 버튼 잘림 0 (UI, D)
- [ ] TalkBack 순서: 관측 "지금 〈제목〉, 세 곡 지났어요, 9분 24초 경과" / D0 "약 네 곡 분량 남았어요, 9분 24초 경과, 평균 길이로 추정". 초마다 낭독 0 (D)
- [x] 재생 버튼 레이블 "음악 일시정지"/"음악 재생", 타이머 일시정지와 시각·레이블 분리 (UI) → 증거: SessionFeatureTest#observed music shows … music pause button (contentDescription "음악 일시정지"/"음악 재생", 타이머는 하단 원형 컨트롤 "세션 잠시 멈춤"/"세션 다시 시작"으로 분리)
- [ ] 문구 리소스화(한국어), 존댓말, Figma 우선 (R)

QA
- [ ] QA-18 곡 표시 OFF + 글꼴 200% → 숫자 타이머·주요 버튼 유지 (UI, D)
- [ ] QA-34 / QA-A35 TalkBack·모션 감소 → 읽기 순서·레이블 구분·정지 레코드 (D)
- [ ] QA-38 곡명·재생 시각 없는 외부 D0 화면 → 가상 곡명 문구 0 (UI, D)
- [ ] QA-A36 모션 줄이기 → 레코드·톤암 정지 이미지 (D)

---

## F05. 외부 음악 등록·열기 (ENG-A05)

위치: domain `model/source`·`model/link` → data `launcher/` → ui `music/`·`share/`. 선행: F00 스파이크(링크 결과).

확인 절차
1. URL 추출·허용 목록·정규화를 표 기반 단위 테스트로 고정 (악성 스킴 포함)
2. `adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "<본문>"`으로 공유 수신 확인
3. 열기 폴백 4단계를 앱 미설치·링크 파손 상태로 실기기 확인

체크리스트
- [x] FR-A023 공유 수신: `ACTION_SEND`(text/plain), 본문 첫 URL 추출 → 허용 목록 → SCR-12. URL 원문은 기기 설정에만 (U, I) → 증거: MusicLinkPolicyTest#extracts…, RegisterSourceUseCaseTest, ShareFeatureTest#a youtube music playlist share…, ManifestIntegrationTest#text shares are received…
- [x] FR-A024 링크 붙여넣기: 같은 검증, 목록 밖이면 저장 거부 + "앱만 열기" 제안 (U, UI) → 증거: RegisterSourceUseCaseTest#an unsupported https link offers app-only…, MusicSheetFeatureTest#pasting a supported link…
- [x] FR-A025 앱만 열기: 설치된 지원 앱만 표시(`<queries>` 범위), 링크 없는 소스로 저장 (I) → 증거: ExternalLauncherIntegrationTest#lists only installed supported apps…, MusicSheetFeatureTest#adding an installed app as app-only…
- [ ] FR-A026 열기: 링크 = `ACTION_VIEW`+`setPackage` → 실행 인텐트 → `market://details?id=` → 수동 안내 / 앱만 열기 = 실행 인텐트 → 스토어 → 수동 안내 (I, D) → 부분 증거: LaunchPlanTest, ExternalLauncherIntegrationTest (링크·앱·스토어·수동) ⟵ 감사(10-10) 미충족: 실기기 QA(D) 기록 없음
- [x] FR-A027 열기 실패는 타이머 무영향: "음악 앱을 직접 열어 주세요" + 내장 소리 1회 제안, 같은 세션·소스에 1회 (UI) → 증거: SessionFeatureTest#nothing to open asks the user to open the music app…
- [x] FR-A028 유튜브뮤직 첫 선택 시 1회 "유튜브뮤직은 Premium이 아니면 다른 앱을 쓸 때 음악이 멈출 수 있어요", 구독 단정 없음 (UI) → 증거: MusicSheetFeatureTest#youtube music shows the premium notice once…
- [x] FR-A029 링크 안전: https만, `javascript:`·`file:`·`intent:`·임의 스킴 거부, 리다이렉트 추적 안 함 (U) → 증거: MusicLinkPolicyTest#non https schemes are rejected…
- [x] §7.5 / DEC-A09 허용 호스트: `music.youtube.com`(`list` 유지, `si`·`feature` 제거) / `open.spotify.com`·`spotify.link`(`si`·`utm_*` 제거) / `music.apple.com`(추적 제거) / 국내 앱은 확정 전 앱만 열기 (U) → 증거: MusicLinkPolicyTest
- [x] DEC-A08 `<queries>`에 지원 패키지만, `QUERY_ALL_PACKAGES` 미선언 (S) → 증거: PrivacyRulesTest#the manifest declares nothing forbidden, ManifestIntegrationTest#declares notification permission and none of the forbidden permissions
- [x] 열기 성공을 재생으로 취급하지 않음 (launch·OS 열기·복귀·재생 구분) (U) → 증거: SessionFeatureTest#opening the music app is not shown as playing until observation sees it (변이 검증: 대기 상태에 재생 버튼 노출 시 실패 확인)

QA
- [ ] QA-19 / QA-A04 앱 미설치·링크 파손·앱 삭제 후 열기 → 스토어·수동 안내, 타이머 유지, 강제 전환 없음 (D)
- [ ] QA-20 / QA-A03 악성 스킴·임의 intent·미지원 링크 → 저장·열기 거부, 앱만 열기 제안, 로그 원문 0 (U, D)
- [ ] QA-21 / QA-A02 유튜브뮤직 플레이리스트 공유 → 저장 → 열기, 추적 파라미터 제거, 첫 선택 고지 1회 (D)

---

## F06. 곡 세기·관측 (ENG-A06)

위치: domain `model/observation` → data `media/` + `service/`(리스너) → ui `counting/`. 선행: F00 스파이크.

확인 절차
1. 집계 규칙(시그니처·30초·반복·인계·강등)은 가짜 메타데이터 이벤트열로 단위 테스트
2. 세션 연결·권한 해제는 계측 테스트 + 실기기(서비스별)
3. 곡 경계 → 칩 반영 지연을 화면 녹화 타임스탬프로 측정

관측 상태 머신 (§5.2)
- [x] OFF→IDLE(권한+설정)→SEARCHING(세션 RUNNING)→OBSERVING→(인계 / SEARCHING / DEGRADED)→OFF·IDLE 전이 전부 (U) → 증거: ObservationStateMachineTest
- [x] DEGRADED 진입: 메타데이터 없음 60초 또는 리스너 끊김. 정상 콜백 재개 시 OBSERVING (U) → 증거: ObservationStateMachineTest#missing metadata or a disconnected listener degrades…
- [x] 관측 상태 변화가 타이머 상태를 바꾸지 않음 (U) → 증거: ObservationCoordinatorTest#observation changes before the target never touch the timer, #a song boundary before the target neither reaches the limit nor pauses (변이 검증: 경계마다 onLimitDue 호출 시 실패 확인)
- [x] SecurityException → OFF, 크래시 0 (I) → 증거: MediaObserverTest#revoked access degrades silently…, ObservationCoordinatorTest#an unexpected media error…

체크리스트
- [x] FR-A030 권한 확인: 앱 시작·복귀·세션 시작 때 `getEnabledListenerPackages`, 변경 시 상태 머신 갱신 (I) → 증거: AndroidMediaSessionGatewayIntegrationTest#access is granted only when…, AndroidCountingAccessIntegrationTest, ObservationCoordinatorTest#access granted later starts observing…
- [x] FR-A031 권한 안내: DEC-A01 시점 SCR-11, [켜기] → API 30+ `ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS`, 그 외 목록 설정, 복귀 반영, [나중에] 후 자동 재제안 0 (I, UI) → 증거: AndroidCountingAccessIntegrationTest#opens this app's notification listener detail page, SettingsFeatureTest#turning counting on shows the prompt…, #later closes the prompt…, MusicSheetFeatureTest#the first external choice explains song counting once…
- [x] FR-A032 리스너에 알림 처리 로직 없음 (`onNotificationPosted/Removed` 오버라이드 0) (S) → 증거: PrivacyRulesTest#app sources follow the privacy rules (listenerOverrides)
- [x] FR-A033 세션 연결: RUNNING 진입 시 `getActiveSessions` + 변경 리스너, DEC-A02 대상 선택(소스 패키지 우선 → 첫 재생 중 → 자기 앱 제외), 전용 핸들러, 콜백 ≤50ms (U, I) → 증거: TargetSelectorTest, AndroidMediaSessionGatewayIntegrationTest, MediaObserverTest#observes the chosen app's playing session…
- [x] FR-A034 / DEC-A03 곡 전환: 시그니처 = 해시(패키지, 제목, 아티스트), 길이 제외. 메타데이터 재발행은 새 곡 아님. RUNNING 누적 30초 미만 미집계, 길이 알고 30초 미만이면 미집계 (U) → 증거: SongCounterTest (30초·광고·재발행·길이 늦게 채움)
- [x] FR-A035 반복: 같은 시그니처 + (재발행 또는 위치가 길이-5초 이상 → 5초 미만) → loops +1 (U) → 증거: SongCounterTest#same signature republished after the song length…, #position rewinding… (DEC-A04 해석은 보류 목록 참고)
- [x] FR-A036 인계: 대상 소실 + 다른 세션 재생 → 인계, 카운트 유지, 질문 0, M-HANDOFF (U, UI) → 증거: SongCounterTest#switching apps hands off…, MediaObserverTest#hands off to another playing app…, MusicPanelStateTest#a handoff names the other app
- [x] FR-A037 조용한 강등: 대상 없음·메타데이터 없음·리스너 끊김·권한 해제 → D0, 팝업 0, 복구 시 재관측 (U, I) → 증거: MediaObserverTest#revoked access degrades silently…, ObservationStateMachineTest#only OBSERVING shows observed data…
- [x] FR-A038 메모리 전용: 제목·아티스트·시그니처가 체크포인트·로그·크래시·분석에 0 (U, S) → 증거: FileCheckpointStoreIntegrationTest#the stored file has no song fields, PrivacyRulesTest(mediaLeaks), ObservationCoordinatorTest#finishing keeps only the integer count… — **DEC-A18로 개정 (2026-10-12)**: 센 곡의 제목·아티스트만 플레이리스트 저장소(`FilePlaylistStore`, noBackup)에 결과 확인 전까지 최대 24시간 저장. 체크포인트·로그·크래시·분석 0은 유지 → 증거: PrivacyRulesTest#app sources follow the privacy rules (songPersisters == FilePlaylistStore만, songLeaks 0)
- [x] FR-A039 세션 밖 해제: READY·ENDED에서 `unregisterCallback`·`removeOnActiveSessionsChangedListener`, 곡 정보 수신 0 (I) → 증거: MediaObserverTest#stopping releases every callback…, AndroidMediaSessionGatewayIntegrationTest#watching forwards … until closed
- [x] FR-A040 이 앱이 본 것: 메모리 로그만, 세션 종료 시 비움 (U, UI) → 증거: MediaObserverTest#the seen log lists…, SeenFeatureTest
- [x] FR-A041 곡 세기 끄기: 즉시 해제 + 시스템 권한 끄는 법 안내, 권한 남아 있어도 관측 0 (I) → 증거: ObservationCoordinatorTest#turning counting off mid session releases…, SettingsFeatureTest#turning counting off explains…
- [x] FR-A042 관측 중 정지 10초 이상 → "음악이 멈췄어요" 비차단 힌트 1회, 타이머 무영향 (DEC-A17 설정을 켜면 타이머 일시정지로 개정) (U) → 증거: MusicStoppedHintTest
- [x] FR-A018 / DEC-A10 현재 곡 제목: 관측 중에만, 꺼짐·대상 없음이면 자리 없음, 가상 곡명 0, 아티스트는 SCR-13에서만, 아트 없음 (UI) → 증거: MusicPanelStateTest, SessionFeatureTest#observed music shows the current title…
- [ ] FR-A022 셔플 안내: 관측으로 셔플 여부를 알 때만 1회 (U) → 부분 증거: ShuffleNoticeTest (규칙만). 남음: 화면 연결 — 플랫폼 MediaController는 셔플 모드를 노출하지 않음 → 근거 출처(사용자 확인 질문 여부) 결정 필요
- [x] O2 집계 값: countedItems(30초 도달 순간 +1), currentIndex, loops, observedFromActiveMs, 칩 채움 = 끝난 센 곡 수 (U) → 증거: SongCounterTest
- [x] DEC-A13 세션 중간 권한 켬 → 즉시 관측, 그 시점부터 집계, mixed (U) → 증거: SongCounterTest#observation started mid session…
- [x] DEC-A14 PAUSED·LIMIT_REACHED 중 곡 전환 미집계(LIMIT_REACHED 경계는 끝나면 멈추기 트리거로만) (U) → 증거: SongCounterTest#time that does not advance while the timer is paused…
- [x] 끊긴 구간 보간 0, confidence mixed (U) → 증거: SongCounterTest#a gap marks the count as mixed…
- [x] OBS_LISTENER_DISCONNECTED → `requestRebind` 1회, 실패 시 DEGRADED (I) → 증거: MediaAccessListenerIntegrationTest#a disconnect asks the system to rebind once, #connecting again does not ask for another rebind, ObservationCoordinatorTest#a listener disconnect degrades observation and blocks commands until it reconnects (끊김 즉시 DEGRADED, 재연결 시 OBSERVING 복귀로 해석)
- [x] 예외·로그에 곡 제목·아티스트·URL·패키지 원문 0 (S) → 증거: PrivacyRulesTest#app sources follow the privacy rules (mediaLeaks), CompositeTimerEffectSink·MediaObserver 예외 메시지 미로깅

QA
- [ ] QA-A05 첫 선택 SCR-11 → [나중에] → 10세션 → 자동 재제안 0 (D)
- [ ] QA-A06 [켜기] → 허용 → 복귀 → M-EXT-WAIT 또는 M-PLAYING (D)
- [ ] QA-A07 서비스 6종 각 3곡 → 칩 3, 제목 갱신, p95 ≤2초, 실패 앱 D0 기록 (D)
- [ ] QA-A08 15초 광고·효과음 → 미집계 (U, D)
- [ ] QA-A09 메타데이터 재발행 → 곡 수 불변 (U, D)
- [ ] QA-A10 한 곡 반복 3회 → loops 3 (앱별 기록) (D)
- [ ] QA-A11 유튜브뮤직 → 멜론 전환 → 인계, 카운트 유지 (D)
- [ ] QA-A12 세션 중 권한 해제 → 크래시 0, 즉시 D0, mixed (D)
- [ ] QA-A13 세션 중간 권한 켬 → "곡 세기 시작 후" (D)
- [ ] QA-A14 PAUSED 중 곡 2개 → 집계 0, 재개 후 이어서 (D)
- [ ] QA-A24 유튜브뮤직 무료 계정 → 복귀 → "음악이 멈췄어요" 1회, 타이머 유지 (D)
- [ ] QA-A25 메신저 알림 20개 → 읽기·로그 흔적 0 (디버그 계측) (I, D)
- [ ] QA-A26 관측 중 패킷 캡처 → 관측 모듈 발신 0 (D)
- [ ] QA-A27 이 앱이 본 것: 세션 중 현재 내용만, 종료 후 비어 있음 (D)
- [ ] QA-A28 곡 세기 끄기 → 즉시 해제, 수신 0, 권한 끄는 법 안내 (D)

---

## F07. 재생 버튼·끝나면 멈추기 (ENG-A07)

위치: domain `model/control`·`model/stopatend` → data `media/` → ui `session/`(음악 패널). 선행: F06.

확인 절차
1. 상태 머신·디바운스·확인 타임아웃은 fake clock 단위 테스트
2. 명령 포트 가짜 구현으로 명령 종류·대상·횟수 검증
3. 서비스별 실기기에서 명령 수용·지연 측정

끝나면 멈추기 상태 머신 (§5.3)
- [x] DISABLED(설정 꺼짐·곡 세기 꺼짐·무한 모드) / IDLE→ARMED / ARMED→STOPPING→STOPPED / CANCELLED / FAILED 전이 전부 (U) → 증거: StopAtEndTest
- [x] targetRevision당 STOPPED·CANCELLED·FAILED 1회 (U) → 증거: StopAtEndTest#an outcome is final for its revision…
- [x] 백그라운드 경계 감지 시 콜백 안에서 `pause`. 경계가 알람보다 먼저 와도 REACH_LIMIT 먼저 처리 (U) → 증거: ObservationCoordinatorTest#at the limit with music playing it arms, then pauses at the next song + ObservationCoordinatorTest#a song boundary past the target before the late alarm reaches the limit first, then pauses once, #a song boundary before the target neither reaches the limit nor pauses (DI: onLimitDue → DispatchTimerCommandUseCase.tick)

체크리스트
- [x] FR-A043 버튼은 M-PLAYING·M-PAUSED·M-HANDOFF에서만, PlaybackState 반영 (UI) → 증거: MusicPanelStateTest#observing playing shows…, #observing paused shows…, #a handoff names…
- [x] FR-A044 탭 1회 = 명령 1회, 대상 세션에만, 300ms 디바운스 (U) → 증거: TransportButtonTest
- [x] DEC-A07 명령 후 2초 안에 기대 상태 미반영 → "응답 없음" 1회 안내, 버튼 → [음악 앱 열기] (U, UI) → 증거: TransportButtonTest, ObservationCoordinatorTest#an app that ignores the button…, SessionFeatureTest#an unresponsive app shows one notice…
- [x] FR-A045 `play` 전송 경로 = 사용자 음악 버튼 + 타이머 연동 재생(DEC-A16) 2곳. 연장·복원·인계·포커스 복귀에서 `play` 0 (S, U) → 증거: PrivacyRulesTest#app sources follow the privacy rules (playCallSites == 1, nonPauseSends == tap `command` + 연동 `MusicCommand.PLAY`), ObservationCoordinatorTest#extending cancels the wait and never plays again
- [x] DEC-A16 (Android 전용, 2026-10-10 사용자 결정) 타이머 일시정지 → 관측 중 재생 음악 일시정지, 이어서 → 이 앱이 멈춘 같은 곡만 재생. 사용자 재생·곡 변경·곡 세기 꺼짐·마침이면 명령 0 (U, UI) → 증거: TimerStateMachineTest#60s run, 30s pause… carry the music link, #a pause that lands past the target…, ObservationCoordinatorTest#pausing the timer pauses the playing music…, #music the user had already paused…, #if the user plays music during the timer pause…, #a different song started during the pause…, #without counting…, #finishing from the timer pause…, SessionFeatureTest#pausing while observed says the music rests too…, #if the observed music keeps playing… + 에뮬레이터 QA(2026-10-10, API 35, 실제 YouTube Music): 타이머 일시정지 → YTM `onPause()`, 이어서 → `onPlay()`
- [x] DEC-A17 (Android 전용, 2026-10-12 사용자 결정, FR-A042 개정) 설정 "음악을 멈추면 타이머도 잠시 멈추기" 기본 꺼짐·곡 세기 필요. 켜면 진행 중 관측 음악이 10초 넘게 멈추면 타이머 일시정지, 재개는 사용자. 연동 일시정지·끝나면 멈추기·음악 앱 사라짐·10초 내 재생이면 동작 0, 콜백 실패 격리 (U, I, UI) → 증거: ObservationCoordinatorTest#with the setting on, music paused for 10 seconds…, #music playing again within 10 seconds…, #with the setting off…, #music paused because the user paused the timer does not loop back, #the stop-at-end pause after the limit…, #a music app that goes away…, #a failing timer callback… (변이 검증 완료), ObservationWiringTest#with the setting on, music paused for 10 seconds pauses the timer, SettingsViewModelTest#pausing the timer with the music needs counting on…, SettingsFeatureTest#the music-pauses-timer switch…, PreferencesSettingsRepositoryIntegrationTest#updates survive an app restart + 에뮬레이터 QA(2026-10-12, API 35, 실제 YouTube Music): 설정 켬 → YTM 일시정지 후 6초 타이머 진행(11:21), 13초 타이머 일시정지(11:25 고정)·"타이머와 음악이 쉬고 있어요"
- [x] FR-A046 `play`·`pause` 외 TransportControls 호출 0 (S) → 증거: PrivacyRulesTest#transport controls other than play and pause are violations + real sources
- [x] FR-A047 설정 기본 꺼짐. 곡 세기 꺼짐이면 비활성 + "곡 세기를 켜야 쓸 수 있어요" (UI) → 증거: MusicSettingsTest, SettingsViewModelTest#stop-at-end is disabled with a reason…, SettingsFeatureTest#stop-at-end is disabled…
- [x] FR-A048 LIMIT_REACHED 진입 시 (설정 켜짐 + 관측 중 + 재생 중)이면 ARMED, 아니면 무동작 (U) → 증거: StopAtEndTest#limit reached while observing…, #music already stopped…
- [x] FR-A049 ARMED 후 첫 곡 경계에서 `pause` 1회 (U) → 증거: StopAtEndTest#the first track boundary after arming sends one pause
- [x] FR-A050 / DEC-A05(b) 상한: 길이·위치 앎 → 남은 길이 + 15초, 모름 → 6분 뒤 `pause` 1회 (U) → 증거: StopAtEndTest#without a boundary the cap…, #without song info the cap is 6 minutes
- [x] FR-A051 [지금 멈추기] 즉시 `pause` / [계속 듣기] 취소·명령 0 (U, UI) → 증거: StopAtEndTest#stop now pauses immediately…, ObservationCoordinatorTest#stop now sends one pause, SessionFeatureTest#waiting to stop at the end offers…
- [x] FR-A052 연장·마침·사용자 직접 정지·대상 소실·곡 세기 꺼짐 → 취소, 명령 0 (U) → 증거: StopAtEndTest#extend, finish, user pause, target loss and counting off cancel…
- [x] FR-A053 멈추지 못하면 SCR-04에 1회 "음악을 멈추지 못했어요. 음악 앱에서 멈춰 주세요" (U, UI) → 증거: StopAtEndTest#pause not confirmed within 2 seconds…, ObservationCoordinatorTest#an unconfirmed pause fails…, SessionFeatureTest#a failed stop asks…
- [ ] FR-A054 관측 대상 외 세션(통화·영상·팟캐스트) 명령 0 (U, D) → 부분 증거: MediaObserverTest#commands go to the observed session only ⟵ 감사(10-10) 미충족: 실기기 QA(D) 기록 없음
- [x] CONTROL_UNRESPONSIVE "음악 앱에서 직접 조절해 주세요" 1회 (UI) → 증거: SessionFeatureTest#an unresponsive app shows one notice…

QA
- [ ] QA-A15 서비스별 버튼 10회 → 탭당 명령 1, 상태 반영, 무시 앱 1회 안내 후 [열기] (D)
- [ ] QA-A16 연장·복원·인계·앱 복귀 각각 → `play` 0 (U, D)
- [ ] QA-A17 끝나면 멈추기 켬, 25분 도달 시 곡 중간 → 대기 문구 → 다음 경계 `pause` 1회 (D)
- [ ] QA-A18 곡 정보 없는 앱 → 6분 후 `pause` 1회 (D)
- [ ] QA-A19 대기 중 [계속 듣기]/[지금 멈추기]/+5분/마침 → 취소/즉시 정지/취소/취소, 추가 명령 0 (U, D)
- [ ] QA-A20 대기 중 음악 앱에서 직접 정지 → 취소, 명령 0 (D)
- [ ] QA-A21 음악이 이미 멈춤 / 무한 모드 → 무동작 (U, D)
- [ ] QA-A22 화면 꺼짐 30분 세션 → 경계 정지, 정지 지연·종료 알림 지연 기록 (D)
- [ ] QA-A23 대상 외 영상 통화·팟캐스트 동시 재생 → 대상 외 명령 0 (D)

---

## F08. 알림·알람 (ENG-A08)

위치: domain `model/notification` → data `notification/`·`alarm/` + `receiver/`. 선행: OS 정책 확인.

확인 절차
1. 채널·알림 내용은 계측 테스트(`NotificationManager.activeNotifications`)
2. 알람은 `adb shell dumpsys alarm`으로 예약·취소 확인
3. 잠금화면·Doze(`adb shell dumpsys deviceidle force-idle`)·제조사 절전은 실기기

체크리스트
- [x] 채널 `session_progress`(LOW, 무음, `setOnlyAlertOnce`) / `session_end`(DEFAULT, 소리 없음·진동) (I) → 증거: SessionNotifierIntegrationTest#channels are a silent low progress channel…
- [x] FR-A061 / SCR-14 진행 알림: RUNNING·PAUSED 게시, 목표 시각 기준 크로노미터 카운트다운, "약 N곡 분량 남음", [일시정지/이어서] [마침], 탭하면 세션 화면, 제목·곡명 0 (I) → 증거: SessionNotifierIntegrationTest#running progress counts down…, #paused progress offers resume and finish…, SessionActionHandlerTest
- [x] FR-A062 진행 알림에 음악 재생·정지 동작 0 (I, S) → 증거: SessionNotifierIntegrationTest#progress never shows the task title, a song or a music button
- [x] FR-A063 Android 13+ 첫 [시작]에서 맥락 설명 후 요청, 거부 시 세션 진행, 재요청 0 (설정에서만) (I, UI) → 증거: AndroidNotificationPermissionIntegrationTest, AllocationViewModelTest#the first start on Android 13+…, #continuing from the explanation…, AllocationFeatureTest#the first start explains notifications…
- [x] FR-A064 / DEC-A06 종료 알림 "정한 시간이 됐어요": `setAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP)`, 앱이 화면에 있으면 화면 안 신호만 (I) → 증거: EndAlarmSchedulerIntegrationTest, SessionNotifierIntegrationTest#limit reached in the background…, #…while the app is on screen…, SessionActionHandlerTest#the end alarm re-evaluates…
- [x] FR-A065 재조정: 키 = sessionId:targetRevision:kind, 이전 revision·PAUSED·ENDED 알림 취소, RUNNING+미래 목표만 upsert (U, I) → 증거: ReconcileSessionUseCaseTest, EndAlarmSchedulerIntegrationTest#a later revision replaces…, AppFlowFeatureTest#a session killed before the target reopens at the limit
- [x] FR-A066 / DEC-A15 사용자가 진행 알림 지움(14+) → 세션 유지, 백그라운드 재게시 0, 앱 재진입 시 재게시 (I) → 증거: SessionNotifierIntegrationTest#the progress notification reports its dismissal…, #a dismissed progress notification is not re-posted…, SessionActionHandlerTest#dismissing the progress notification…
- [x] 진행 알림은 포그라운드 서비스와 묶지 않음. 내장 소리 재생 중에만 mediaPlayback FGS가 같은 알림 사용 (I) → 증거: NoisePlaybackServiceIntegrationTest#noise rides on the session progress notification instead of posting a second one, #without a session the sound notification uses the same id…, SessionNotifierIntegrationTest#the posted progress notification is shared… (진행 알림 자체는 SessionNotifier 일반 알림) (검증 범위: 진행 알림은 SessionNotifier가 일반 notify로만 게시 — 서비스 클래스 아님, 소리 서비스가 같은 ID 재사용)
- [x] `SCHEDULE_EXACT_ALARM`·`USE_EXACT_ALARM`·specialUse FGS 미선언 (S) → 증거: PrivacyRulesTest#forbidden manifest declarations…, ManifestIntegrationTest
- [x] 무한 모드 자동 목표 알림 없음 (U) → 증거: TimerStateMachineTest#infinite mode has no target, no alarm…
- [ ] 정시 보장 문구 0 (R)

QA
- [ ] QA-35 집중 모드·절전·알림 거절 → 정시 보장 문구 없음, 복원 정상 (D)
- [ ] QA-39 목표 직전·저장 직후·알림 예약 직후 강제 종료 → 재조정 후 현재 키 예약만 유지 (I, D)
- [ ] QA-A29 잠금화면 진행 알림 → 카운트다운·"약 N곡 분량 남음"·동작 2개, 제목·곡명·음악 버튼 0 (D)
- [ ] QA-A30 Android 14+ 진행 알림 지움 → 세션 유지, 재진입 시 재게시 (D)
- [ ] QA-A31 알림 권한 거부 → 세션·종료 화면 정상, 재요청 0 (D)
- [ ] QA-A37 삼성 절전·앱 절전 → 관측·알림 지연 기록, 정시 보장 문구 0 (D)

---

## F09. 내장 소리 (ENG-A09)

위치: domain `model/audio` → data `audio/`(Media3) + `service/`(FGS) → ui `music/`. 선행: 오디오 스파이크.

확인 절차
1. 노이즈 생성기(화이트·핑크)·페이드 곡선은 단위 테스트
2. 오디오 포커스·헤드폰 분리·통화는 실기기
3. 30분 재생 녹음으로 끊김·팝 확인

체크리스트
- [ ] FR-A055 화이트·핑크 노이즈 오프라인, 기본 볼륨 20%, limiter, 볼륨 UI는 시스템 볼륨과 구분 (U, D)
- [ ] FR-A056 소리 시작은 앱이 화면에 있을 때 사용자 탭으로만. 마지막 소스 기억으로 자동 재생 0 (UI, D) → 부분 증거(에뮬레이터 API 35): NoisePlaybackDeviceTest#playingStartsTheMediaPlaybackForegroundServiceAndRealAudio (앱 화면에서 시작 → mediaPlayback FGS·진행 알림 ID 1·실제 오디오). 남음: 실기기 QA
- [ ] FR-A057 시작 1.5초 페이드 인, 일시정지 시 30% 덕킹, 종료·도달 3초 페이드 아웃 (U, D)
- [ ] FR-A058 헤드폰 분리·통화 → 앱 소리 정지, 타이머 유지, 자동 재개 0 (D) → 부분 증거(에뮬레이터): NoisePlaybackDeviceTest#anotherAppTakingAudioFocusStopsTheSoundWithoutResuming (포커스 상실 → 정지·서비스 종료·자동 재개 0, 변이 검증 완료). 남음: 실제 헤드폰 분리·통화 실기기 QA
- [ ] FR-A059 외부→내장 전환: 관측 중·외부 재생 중이면 "음악 앱을 멈출까요?" [멈추기] [그대로], 곡 세기 꺼짐이면 직접 정지 안내 (UI, D)
- [ ] FR-A060 과제 유형 제안: 태그 없으면 질문 0, 거절해도 시작 가능, 효과 보장 문구 0 (UI)
- [x] mediaPlayback FGS는 내장 소리 재생 중에만, 무음 재생 0 (I, S) → 증거: NoisePlaybackServiceIntegrationTest (non-exported mediaPlayback 선언, 재생 시에만 서비스 시작, 정지 시 stopSelf), PrivacyRulesTest(specialUse 금지) + NoiseControllerTest#at zero volume nothing plays, so there is no silent playback, MusicPanelStateTest#noise at zero volume is shown as muted
- [x] 노이즈를 곡으로 세지 않음 (completedTracks null) (U) → 증거: MediaObserverTest#never observes this app's own session (자체 소리는 관측 대상 아님), NoiseTest

QA
- [ ] QA-22 노이즈 명시 재생·정지·종료 → 페이드·덕킹 정상, 곡 카운트 null (D)
- [ ] QA-23 헤드폰 분리·통화·재연결 → 자체 소리 정지, 자동 재개 없음 (D) → 부분 증거: 위 FR-A058과 같음
- [ ] QA-24 / QA-A33 외부↔내장↔무음 전환 → 단일 소스, 선택 없이 외부 명령 0 (D)
- [ ] QA-A34 Android 15·17 백그라운드 노이즈 시작 시도 → 경로 없음 (D)

---

## F10. 결과·메모·설정·삭제 (ENG-A10)

위치: domain `model/result`·`memo`·`settings` → data `local/`·`repository/` → ui `result/`·`memo/`·`settings/`. 선행: DEC-02·07.

확인 절차
1. 결과 계산·콜백 페이로드는 단위 테스트(곡 제목 필드 부재 확인)
2. 설정 저장은 앱 재시작 계측 테스트
3. 삭제는 실기기에서 저장소·알림·알람·관측 해제 확인

체크리스트
- [x] FR-A067 중립 결과: ~~최초 배정·실제 시간 병기, 출처 라벨~~, 초과율·실패·축하·점수 0 (UI) — **DEC-A20으로 개정 (2026-10-13)**: 배정 병기·출처 라벨 제거, 총 걸린 시간만 표시 → 증거: ResultFeatureTest#the result stays neutral, without failure, overrun, praise or score (FR-A067), #the result has no comparison, summary, icons, caption or task title (DEC-A20)
- 제거됨 (DEC-A20): ~~FR-A068 체감 시간 0~1,440분 또는 건너뛰기, 스킵을 0으로 기록하지 않음 (U, UI)~~ — 결과 화면 질문 삭제 (2026-10-13). 체감 시간은 수집하지 않음
- [x] FR-A069 생각 메모: 500자, 활성 세션 20개, 분석 전송 0, 저장 실패 시 입력 유지, 미선택 24시간 뒤 삭제 (U, UI) → 증거: ThoughtMemosTest(500자·20개·24시간 보관 규칙), PreferencesMemoRepositoryIntegrationTest, MemoFeatureTest#a save failure keeps the typed text…, AppFlowFeatureTest#memos in the result can be kept or deleted, ResultFeatureTest#memos saved during the session are listed and can be kept or deleted (FR-A069) (DEC-A20 후에도 결과 화면 목록 아래 유지) (분석 전송 경로 없음: AnalyticsTest·PrivacyRulesTest)
- [x] FR-A070 설정: 버전 있는 키-값, 재시작 유지, 페이드 0~5초/0~10초, 볼륨 0~1, 기본값 복구. 곡 세기·끝나면 멈추기·저장 소스 포함 (U, I) → 증거: PreferencesSettingsRepositoryIntegrationTest, SettingsViewModelTest (평균 곡 길이 1~30분 단위로 축소 구현) + NoiseEnvelopeTest#fade lengths follow the settings, #sound setting ranges…, SettingsViewModelTest#sound volume and fades are set within range and reset to defaults, NoiseControllerTest#the set volume and fade lengths are used, PreferencesSettingsRepositoryIntegrationTest#updates survive an app restart (sound 포함), SettingsFeatureTest#built-in sound volume and fades can be adjusted and reset + PreferencesSettingsRepositoryIntegrationTest#values written under another version are not trusted… (버전 키, 변이 검증 완료), #out-of-range stored sound and loop values fall back to defaults
- [x] FR-A071 데이터 삭제: 설정·활성 상태·메모·이력·저장 소스·링크 삭제, 소리 중지, 알림 취소, 관측 해제, 권한 끄는 법 안내 (I) → 증거: DeleteAllDataUseCaseTest, SettingsFeatureTest#deleting all data…, AppFlowFeatureTest#deleting all data during a session…
- [ ] FR-A072 결과 콜백: sessionId·deliveryId·taskId·시간·endReason·extensionUsed, 곡 제목 0, 중복 방지 키 (U)
- [x] 결과 복원본 최대 24시간, 닫으면 삭제 (U) → 증거: RestorePolicyTest#an ended session's result is shown again for up to 24 hours, AppFlowFeatureTest#a recent result is shown again after relaunch and closing it deletes it
- [x] 작업별 마지막 시간 opt-in, taskId당 1건, 30일 TTL (U) → 증거: TaskHistoryTest

QA
- [x] QA-28 메모 저장 실패·취소 → 입력 유지·취소, 타이머 유지 (UI) → 증거: MemoFeatureTest#a save failure keeps the typed text…, #cancel returns without saving
- [ ] QA-29 결과 닫기·이력 동의 OFF → 곡 정보 폐기, 작업별 시간 저장 없음 (U, D)
- [ ] QA-30 이력 opt-in·같은 taskId 재진입 → 마지막 시간만 제안 (U, D)
- [ ] QA-32 콜백 오류 후 재시도 → 로컬 결과 유지, deliveryId 중복 처리 (U)
- [ ] QA-33 / QA-A38 데이터 전체 삭제 → 소스·설정·체크포인트 삭제, 오디오·알림 취소, 관측 해제, 권한 끄는 법 안내 (D)

---

## F11. 정적 검사·CI (ENG-A11)

위치: 빌드 규칙(lint 커스텀 규칙 또는 Gradle 검사 태스크). 대상: `data/media`, `service/`, 매니페스트. 선행: F06·F07.
규칙 위반 시 빌드 실패해야 완료.

- [x] NFR-A013-1 `NotificationListenerService` 하위 클래스의 `onNotificationPosted`/`onNotificationRemoved` 오버라이드 0 (S) → 증거: PrivacyRulesTest#listener override… + real sources
- [x] NFR-A013-2 `TransportControls.play()` 호출 지점 1 (버튼 핸들러) (S) → 증거: PrivacyRulesTest#more than one play call site… + real sources (정확히 1)
- [x] NFR-A013-3 `play`·`pause` 외 TransportControls 메서드 호출 0 (S) → 증거: PrivacyRulesTest#transport controls other than play and pause… + real sources
- [x] NFR-A013-4 관측 패키지에서 네트워크 API·로그 출력 0 (S) → 증거: PrivacyRulesTest#logging or network in the media package… + real sources
- [x] 매니페스트 금지 선언 0: `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, `QUERY_ALL_PACKAGES`, `READ_MEDIA_AUDIO`, `MEDIA_CONTENT_CONTROL`, 접근성 서비스, specialUse FGS (S) → 증거: PrivacyRulesTest#forbidden manifest declarations…, #xml comments are not declarations, #the manifest declares nothing forbidden
- [x] 위반 샘플로 빌드 실패 확인 후 샘플 제거 (S) → 증거: PrivacyRulesTest 샘플 테스트 5종 + 실제 매니페스트 주석 오탐을 실패로 검출 후 규칙 수정(2026-10-10)

---

## F12. 분석 이벤트

기본 OFF. 동의 시 허용 필드만. 선행: 동의 UI·sink 결정.

- [x] 이벤트 7종 허용 필드만: `mt_a_permission_prompted`(trigger, result) / `mt_a_observation_state`(provider enum, quality, reason) / `mt_a_player_switched` / `mt_a_fallback_to_estimate` / `mt_a_control_sent`(command, origin, applied) / `mt_a_stop_at_end`(outcome, wait_s 버킷) / `mt_source_registered`(method, provider) (U) → 증거: AnalyticsTest#with consent each event carries only its allowed fields, #nothing is sent without consent
- [x] 곡 제목·아티스트·URL·패키지 원문·센 곡 수 포함 0, 목록 밖 provider는 OTHER (U, S) → 증거: AnalyticsTest (enum·boolean 필드만, WaitBucket 구간화), PrivacyRulesTest#no analytics or crash SDK is linked, LaunchPlanTest (목록 밖 package → Provider.OTHER)
- [x] 곡 전환·반복·보정은 메모리 UI 버스만, 분석 SDK 연결 0 (S) → 증거: PrivacyRulesTest#no analytics or crash SDK is linked (분석 SDK 미연결)
- [ ] QA-31 분석 OFF/ON 네트워크 검사 → OFF 0건, ON 허용 필드만 (D)
- [ ] QA-42 동의 철회·오프라인 전체 삭제 → 큐·저장값 삭제 (D)

---

## F13. 비기능

- [ ] NFR-A001 오프라인 완결 (곡 세기 포함) — QA-36 비행기 모드 60분 (D)
- [ ] NFR-A002 60분 표시 오차 ≤1초 (U, D)
- [ ] NFR-A003 START/PAUSE 탭 반응 p95 ≤100ms, 저장 I/O가 프레임 차단 0 (D)
- [ ] NFR-A004 cold start p95 ≤2초 (대표 중급 기기) (D)
- [x] NFR-A005 원자 체크포인트, 마이그레이션 fixture, 다중 탭·강제 종료로 상태 중복 0 (U, I) → 증거: FileCheckpointStoreIntegrationTest, TimerStateMachineTest#a repeated command id…, SessionUseCasesTest#read-modify-write … under the repository lock + FileCheckpointStoreIntegrationTest#a v1 fixture without later optional fields still restores (fixture `app/src/test/resources/checkpoint/v1-minimal.json`, 변이 검증 완료) + FileCheckpointStoreIntegrationTest#a failed write keeps the previous checkpoint intact (AtomicFile, 변이 검증: 제자리 쓰기로 바꾸면 실패). 강제 종료 중복 0은 command id 멱등 + 복원 테스트로 간접
- [ ] NFR-A006 곡 세기 켠 60분 세션: 폴링·wake lock 0, 콜백 외 주기 작업 0, 켠/끈 소모 비교 기록 (D)
- [ ] NFR-A007 노이즈 30분 끊김·팝 0 (D)
- [ ] NFR-A008 링크 허용 목록, 민감 로그 0, 관측 모듈 네트워크 호출 0 (S, D)
- [ ] NFR-A009 접근성 검수 통과 (F04) (D)
- [ ] NFR-A010 호스트 분리: standalone 어댑터·mock host에서 같은 타이머 테스트 통과 (U) → 부분 증거: TimerStateMachineTest·SessionUseCasesTest가 fake 시계·저장소로 동일 동작 ⟵ 감사(10-10) 미충족: standalone 어댑터·mock host 분리 테스트 없음
- [ ] NFR-A011 주요 흐름 blocker·crash 0 (SecurityException 포함) (D)
- [ ] NFR-A012 곡 경계 → 칩·제목 반영 p95 ≤2초 (화면 켜짐) (D)
- [ ] NFR-A014 경계 감지 → `pause` 전송 p95 ≤1초 (화면 꺼짐 포함) (D)
- [ ] QA-36 네트워크 OFF 60분 → 코어 동작, 오차 ≤1초, 무한 재시도 0 (D)

---

## F14. 로컬 파일 (A-P3, 후속)

A-P1에서는 인터페이스만, 메뉴 숨김.

- [ ] A-P1: O0 인터페이스 존재 + 메뉴 숨김 (UI)
- [ ] FR-A073 / SCR-10 SAF 선택기 + 지속 URI 권한, 전체 미디어 권한 없음, mp3/m4a/wav (I, D)
- [ ] FR-A074 큐 순서 유지, 자연 종료만 completedTracks, skip/seek 구분, 반복은 앱 플레이어에만 (U)
- [ ] QA-25 정상·손상·DRM 파일 → 정상만 재생, 타이머 유지 (D)
- [ ] QA-26 자연 종료·skip·seek·repeat → 중복·오산 없음 (U, D)
- [ ] QA-27 / QA-37 접근 철회·프로세스 복원 → 다시 선택, unknown/mixed (D)

---

## F15. Play 제출 (ENG-A12)

- [ ] 앱 내 사전 고지(SCR-11) 문구 검수: 시스템 경고 문구 먼저 설명 (R)
- [ ] 스토어 설명: 곡 전환 읽기·재생/일시정지, 다른 알림 읽지 않음 (R)
- [ ] 데이터 보안 양식 "수집·전송 없음"과 실제 동작 일치 (R, D)
- [ ] 개인정보 처리방침: 알림 접근 범위, 미수집·미전송, 세션 종료 시 폐기 (R)
- [ ] 데모 영상 3종: 권한 없이 D0 / 곡 세기·버튼 / 이 앱이 본 것 (D)
- [ ] FGS 유형(mediaPlayback) 선언 (R)
- [ ] 내부 테스트 트랙 통과 (D)

---

## F16. 릴리스 게이트 (SRS §15.1)

- [ ] A-P0 스파이크로 지원표 확정 (ADR-0014 재검토 여부 기록)
- [ ] DEC-01·02·04·07, DEC-A01·A05·A06 승인 기록
- [ ] targetSdk·minSdk 확정, 대표 기기 고정
- [ ] FR·NFR·QA(13.1 + 13.2) 전부 통과, NFR-A013 CI 적용
- [ ] SCR-11 문구 검수
- [ ] Play 준비물(PRD 9.3) 완비, 데이터 보안 양식과 실제 동작 일치
- [ ] 정책 해석(서비스 SDK·API 미사용, OS 경로만) 법률 검토
- [ ] 효과·의학 주장, 정확도·전 서비스 지원·정시 알림 보장 문구 0
- [ ] 출시 후 권한 유지 판정(PRD 13.3) 측정 준비, 분석 동의 OFF 사용자 측정 0

---

## F17. 디자인 시스템·컴포넌트·에셋·와이어프레임 (ENG-A04)

위치: ui `theme/`·`component/`·`navigation/`, `res/drawable`, `res/font`. 사양: `docs/design/README.md`.
Figma: `0tVXNjTmVjGrpIjPcm977K` — Design System(0:1), Assets / Component(1:2), Gui(1:3), User Flow(1:4).
MCP 연결 확인됨(2026-10-10, Starter View 좌석 → 호출 한도 작음). 사양 문서 먼저, MCP는 누락 항목만.

확인 절차
1. 토큰은 `Theme.kt` 상수와 사양 표를 1:1 대조하는 단위 테스트 (색 hex·sp·dp)
2. 컴포넌트는 Compose Preview + 스크린샷 테스트를 `docs/design/screens/`·`lock-screen-and-components.png`와 나란히 비교
3. 화면 이동은 와이어프레임 W01~W10 이동표대로 UI 테스트
4. 충돌 문구는 SRS 문구로 구현됐는지 문자열 리소스 검사
5. 대비·터치 영역은 실기기 접근성 검사기(Accessibility Scanner)로 확인

디자인 시스템 (Design System 0:1)
- [x] 연결 점검: Figma MCP `whoami`·`get_metadata` 성공, 한도 내 추가 추출 계획 기록 (R) → 증거: 2026-10-10 whoami·get_metadata(0:1, 1:4) 성공, 한도 정책은 docs/design/README.md 상단 기록
- [x] 색 토큰 Light·Dark (background·surface·field·ink·muted·data·charcoal·navy·vinyl·accent 4종) (U) → 증거: DesignTokensTest#light palette…, #dark palette…, #accent and contrast colors match
- [x] sunset 그라데이션(`#FF9C3F`→`#FF7544` 48%→`#FF3B4D`), CTA·선택·진행 강조에만 (UI) → 증거: DesignTokensTest#sunset gradient stops are orange 0, coral 48 percent, red 100 percent, SunsetUsageTest#sunset is used only by the primary button, the selected chip and the record label, #every sunset brush uses the design stops
- [ ] 테마 Light / Navy / Dark 전환 구조 (V1 확정은 F00) (UI)
- [ ] 그림자 raised·inset(Light·Dark)·accent.glow·vinyl·field (UI)
- [ ] 폰트: Plex(400·500·700) + Montserrat variable 등록, 한글·숫자 span 혼용, `tnum` (UI, D)
- [x] 타입 스케일 12역할 (sp/행간·굵기) (U) → 증거: DesignTokensTest#type scale matches all 12 design roles (변이 검증 완료)
- [x] 간격(4~48)·모서리(16/24/26/30/pill)·컴포넌트 크기 표 (U) → 증거: DesignTokensTest#spacing, radius and component sizes follow the design table (변이 검증 완료). 토큰 정의만 — 화면 적용은 SCR 대조 항목에서
- [ ] 터치 영역 44 → 48dp 상향 반영 (UI) → 메모(10-10): 버튼 레이아웃은 40dp이고 Compose가 터치 판정만 48dp로 확장 → JVM 테스트로 의미 있게 검증 불가. 기기에서 접근성 검사기로 확인 필요
- [ ] 흰 글자 ↔ sunset 대비 2.08~3.51:1 미해결 → 디자이너 결정 기록 (R)

컴포넌트 (Assets / Component 1:2)
- [x] `PrimaryButton` Default·Pressed·Disabled (UI) → 증거: PrimaryButtonStyleTest (3), PrimaryButtonFeatureTest#a disabled primary button is announced as disabled and ignores taps, #an enabled primary button takes a tap (Pressed 정지점 coral→red는 컴포넌트 시트에서 읽은 해석 → 디자인 확인 보류)
- [x] `TimeChip` 기본·선택 / `CircleIconButton` 8종 (UI) → 증거: CircleIconButtonFeatureTest#the eight circle icons map to the design icons, #a circle icon button is 48dp, labelled and clickable, #time chips announce which one is selected (`ui/component/CircleIconButton.kt`; play 아이콘은 에셋에 없어 시트 모양대로 그림 `ic_mt_play.xml` → 디자인 확인 보류. 화면 적용은 SCR 대조 항목에서)
- [ ] `TranslationCard` 다이얼(곡 수만큼 조각 Canvas) Light·Navy·Dark (UI) → 부분 증거: DialSegmentsTest (5), AllocationFeatureTest#shows 25 minutes as about 7 songs…, #the translation card names its basis… ⟵ 남음: Navy·Dark 화면 대조
- [x] `ProgressPanel` 칸 막대·현재 칸 outline·마지막 곡 상태 (UI) → 증거: ProgressCellsTest (4), SessionFeatureTest#seven songs are drawn as progress cells with the current one outlined, #in the last song only the last cell is outlined (`ui/component/ProgressPanel.kt`; 패널 카드 외곽 350×134는 SCR 대조에서)
- [x] ~~`ComparisonCard` (SRS 출처 라벨 문구)~~ / `TaskField` (UI) → 증거: AllocationFeatureTest#start begins the session with the typed title… (`TaskField.kt`) — **DEC-A20 (2026-10-13)**: `ComparisonCard`·`SongIcons`는 결과 화면에서 빠져 코드 삭제
- [ ] `Turntable` 레코드(286dp, 동심원 28개)+톤암(30°/0°, 650/550ms), DEC-A12 조건 (UI, D)
- [ ] 진행 알림: Figma Android system template 3종(실행·멈춤·잠금), MediaStyle 없음 (I, D)

에셋 (`docs/design/assets/`)
- [x] 아이콘 9종 SVG → Vector Drawable (24dp, 2dp stroke) (UI) → 증거: IconResourcesIntegrationTest#all nine design icons load at their design size, #every icon path is a 2dp round stroke without fill (`res/drawable/ic_mt_*.xml`; chevron 16dp·note 22dp는 원본 viewBox 유지)
- [ ] 레코드·톤암 SVG → Vector Drawable, PNG(광택·결과 점선) 밀도 처리 (UI)
- [ ] 폰트 OFL 라이선스 앱 내 고지(오픈소스 라이선스 화면 또는 설정) (R)

화면 (Gui 1:3)
- [ ] SCR-01 ↔ `light-01-home` / Navy·Dark 홈 (UI)
- [ ] SCR-02 D0 ↔ `light-02-estimated`, 관측 ↔ `light-02-running` (UI)
- [ ] SCR-03 ↔ `light-02-paused`, 끝 예고 ↔ `light-02-last-song` (UI)
- [ ] SCR-05·07·08 ↔ `light-03-result`·`light-07-correct-song-count`·`light-06-capture` (UI) — DEC-A20: SCR-05 기준 = `figma-result-playlist.png` 시안 A(위·빈 상태), SCR-07(결과 진입) 제거
- [ ] 피그마에 없는 SCR-04·06·09·11·12·13을 토큰·컴포넌트로 구성, 디자이너 확인 (UI, R)

와이어프레임 (User Flow 1:4)
- [ ] 세션 흐름 4:1327 순서 (할 일 → 배정 → 환산 → 시작 → 진행 → 마지막 곡 → 결과 → 다음 행동) (UI)
- [ ] W01~W10 이동표대로 내비게이션 (UI) → 부분 증거: AppFlowFeatureTest#first launch…(W01→W02→W04), #settings and music selection…(W01·W09), #the session can open music selection…(W02→W09), #a thought memo…(W07), #wireframe moves W02 to W06 and back, W06 finish to W04 (W08 결과 진입은 DEC-A20으로 제거), #W01 open-ended start goes to W10 (남음: W05는 별도 화면 대신 배정 화면 인라인 입력 — 디자인 확인 필요)
- [ ] 충돌 문구 8건을 SRS 문구로 구현 (반말 → 존댓말, 청취 단정 제거, "외부 음악 제어 없음" → ADR-0013 반영) (UI, R)
- [x] 잠금화면 규칙 8:1240 Android 항목: 별도 채널, 시스템 서체, 제목·곡명 숨김 (I) → 증거: SessionNotifierIntegrationTest#channels…, #progress never shows the task title, a song or a music button (별도 채널, 시스템 템플릿·서체, 제목·곡명 0)

## F18. 세션 플레이리스트·공유 이미지

위치: 계획 `docs/plans/playlist-share.md`. 디자인 Figma `46:6540` (`docs/design/screens/figma-result-playlist.png`). 결정 DEC-A18(곡 기록 결과 확인 전까지 기기 저장, FR-A038 개정)·DEC-A19(결과 = 시안 A)·DEC-A20(결과 = 시안 A만, 기존 결과 항목 제거).

- [x] DEC-A18 (2026-10-12 사용자 결정, FR-A038 개정) 세션 중 센 곡의 곡명·아티스트를 결과 확인 전까지(최대 24시간) 기기 저장. 결과 닫기(완료·닫기. "같은 일 이어서"는 DEC-A20으로 제거)·데이터 전체 삭제·24시간 경과 시 삭제. 로그·분석·백업 제외 (U, I, S) → 증거: 아래 M1·M2 증거 + PrivacyRulesTest#song titles stored outside the playlist store are violations (DEC-A18), #logging or network next to playlist entries is a violation (DEC-A18)
- [x] DEC-A19 (2026-10-12 사용자 결정, SCR-05 개정) 결과 화면 = 시안 A 플레이리스트. 주 버튼 [다음 할 일 고르기] → [완료] (동작 같음). [인스타로 공유하기]는 M5~M7 기획 후 (UI) → 증거: ResultFeatureTest#완료 closes the result and begins a new allocation (DEC-A19), #the share button is shown but disabled until sharing is built (DEC-A20, M5-M7 pending)
- [x] DEC-A20 (2026-10-13 사용자 결정, SCR-05·SCR-07·FR-A067·FR-A068·W04·W08 개정) 결과 화면 = 시안 A만: 닫기·"이번 플레이리스트"·총 걸린 시간·곡 목록(빈 상태·일부 관측 안내)·메모 보관/지우기(FR-A069, 목록 아래)·[완료]·[인스타로 공유하기](M5~M7 전까지 비활성, 빈 상태도 비활성, 클릭·인텐트 없음). 제거: 비교 카드·요약 문장·곡 아이콘·캡션·작업 제목·곡 분량 고치기·체감 시간·"같은 일 이어서 하기" (UI) → 증거: ResultFeatureTest#the share button is shown but disabled until sharing is built (DEC-A20, M5-M7 pending), #the share button is disabled in the empty state too (Figma A empty), #the disabled share button tells screen readers it is not ready yet, #the result has no comparison, summary, icons, caption or task title (DEC-A20), #the result offers no song correction, felt-time question or same-task continue (DEC-A20), #memos saved during the session are listed and can be kept or deleted (FR-A069), AppFlowFeatureTest#a finished session shows the songs counted during it and 완료 deletes them (F18 M2, M4)
- [x] M1 재생 기록: 관측 중 곡 세기가 센 곡(30초 규칙·광고 제외·일시정지 중 시간 정지)만 순서대로 곡명·아티스트·앱 기록, 반복 보존. 한 곡 반복(loop)은 곡 수와 같이 새 항목 아님 (U) → 증거: PlaylistRecordingTest (7), MediaObserverTest#each counted song is reported once with title, artist and app, in play order with repeats (F18 M1), #the final count at finish reports a song that just reached 30 seconds…, #nothing is reported while counting is off (F18 M1), #nothing is reported without notification access (F18 M1), #a failing playlist callback neither stops observation nor the count (MT-T7), ObservationCoordinatorTest#counted songs are reported with the live session id…, #songs passing during a timer pause are not reported (F18 M1, QA-A14), #a failing playlist callback keeps counting (MT-T7), ObservationWiringTest#counted songs are appended to the session playlist in play order (F18 M1, DEC-A18), #a failing playlist store does not stop counting…
- [x] M2 기록 보관: noBackup 원자 저장, 결과 닫기·데이터 전체 삭제·24시간 경과 시 삭제, 손상은 빈 목록, 로그·분석 0 (U, I) → 증거: FilePlaylistStoreIntegrationTest (7), SessionPlaylistTest (2), DeleteAllDataUseCaseTest#cancels the live session's alarm…(playlists 비움), RestoreSessionUseCaseTest#app start purges session playlists older than 24 hours…, #a corrupt checkpoint still purges expired playlists, AppFlowFeatureTest#after a process death the result shows the stored playlist and 완료 deletes it…, #closing the result with X deletes its playlist (F18 M2), #a finished session shows the songs counted during it and 완료 deletes them…, #expired playlists are purged when the app starts (F18 M2)
- [x] M3 총 걸린 시간 "N분 N초" (일시정지 제외 = 세션 활성 시간, 초 내림, 1분 미만 "N초", 1시간 이상 "N시간 N분 N초") (U, UI) → 증거: TotalTimeTest (4), ResultUiStateTest#total time is the active session time without pauses…, ResultFeatureTest#the result opens with this session's playlist and the total time without pauses (F18 M3), #total time shows seconds only under a minute…, #total time past an hour names hours, minutes and seconds (F18 M3)
- [x] M4 결과 화면 A: 이번 플레이리스트·총 걸린 시간·곡 목록(화면 스크롤)·하단 고정 버튼·빈 상태·일부 관측 안내, 메모 카드 목록 아래 유지(비교·보정·체감 시간은 DEC-A20으로 제거), 복원 후 저장된 목록 표시 (UI) → 증거: ResultFeatureTest#songs are listed with title and artist in play order, repeats kept (F18 M4), #an unknown artist is left out rather than invented, #without confirmed songs the empty state says so…, #a session counted only part of the time says the list covers only that part…, #a fully observed session has no partial note, #with a long list the buttons stay fixed and the memos stay reachable below the list (F18 M4, FR-A069), AppFlowFeatureTest#after a process death the result shows the stored playlist… (Figma 시각 대조는 F17 SCR-05 항목에서)
- [ ] M5 공유 이미지 1080×1920 PNG 기기 내 렌더, 분할 규칙, 작업 제목 제외 (U, I) — 추가 기획 필요
- [ ] M6 공유 미리보기 화면 (UI)
- [ ] M7 인스타 스토리 인텐트 + FileProvider, 미설치 시 공유 시트, `<queries>` 인스타 1개 (I, D)
- [ ] M8 SRS·Play 데이터 보안 양식에 곡명 내보내기(사용자 탭 한정) 반영 (R)

