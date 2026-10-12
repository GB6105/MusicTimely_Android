# 아키텍처·폴더 구조

- 채택: Android 공식 앱 아키텍처 가이드의 **MVVM + 계층 구조** (UI → Domain → Data)
- 웹 서버 MVC 대응: View = Compose Screen, Controller = ViewModel, Model = Domain(UseCase·Model) + Data(Repository)
- DI: Hilt. 상태: `StateFlow<UiState>` 단방향 흐름 (UDF)

## 계층

| 계층 | 모듈·패키지 | 책임 | 의존 방향 |
|---|---|---|---|
| UI | `:app` `ui/` | Screen(Compose) + ViewModel + UiState | → Domain |
| Domain | `:domain` | Model·상태 머신·계산, Repository 인터페이스, UseCase | 의존 없음 (순수 Kotlin) |
| Data | `:app` `data/` | Repository 구현, 데이터 소스(저장소·미디어 세션·알람·알림·오디오·외부 앱) | → Domain 인터페이스 구현 |
| Android 컴포넌트 | `:app` `service/`, `receiver/` | OS가 생성하는 진입점. 받은 이벤트를 Repository로 넘김 | → Data |
| 테스트 대역 | `:testing` | 손으로 쓴 fake (`FakeClock`, `Fake*Repository`, `Fake*Port`). `:domain`·`:app` 테스트가 공유 | → Domain |

- UI는 Data를 직접 참조하지 않음 (ViewModel → UseCase → Repository 인터페이스)
- 플랫폼 API(MediaSessionManager·AlarmManager·NotificationManager·TransportControls)는 `data/` 안에서만 호출
- 시간은 `ClockPort`(Domain 인터페이스)로만 읽음 → 테스트에서 fake clock 주입

## 폴더 구조

```
domain/src/main/kotlin/com/gb6105/musictimely/domain/
├── model/<기능>/          엔티티·값 객체·순수 상태 머신·계산기
├── repository/            Repository 인터페이스 (+ ClockPort 등 포트)
└── usecase/<기능>/        UseCase (클래스 하나 = 동작 하나, operator fun invoke)

app/src/main/java/com/gb6105/musictimely/
├── MusicTimelyApplication.kt   @HiltAndroidApp
├── MainActivity.kt             @AndroidEntryPoint, 내비게이션 호스트
├── di/                         Hilt 모듈 (Repository·데이터 소스 바인딩)
├── data/
│   ├── repository/             Repository 구현
│   ├── local/                  설정·체크포인트 원자 저장
│   ├── media/                  미디어 세션 관측(곡 세기)·TransportControls
│   ├── launcher/               외부 음악 앱 열기·공유 링크 처리
│   ├── audio/                  Media3 내장 노이즈
│   ├── notification/           채널·진행 알림·종료 알림·알림 권한
│   ├── alarm/                  비정확 알람 예약
│   └── effect/                 타이머 부수효과 → 기능별 handler (Hilt `@IntoSet`)
├── service/                    MediaAccessListener(알림 접근), NoisePlaybackService(mediaPlayback FGS)
├── receiver/                   알람 수신, 알림 동작 수신
└── ui/
    ├── theme/                  토큰 (docs/design/README.md)
    ├── component/              공용 컴포넌트 (PrimaryButton, Turntable …)
    ├── navigation/             화면 이동 (와이어프레임 W01~W10)
    └── <기능>/                 XxxScreen.kt · XxxViewModel.kt · XxxUiState.kt
```

## 기능 패키지 대응

| 기능 (체크리스트) | domain `model/`·`usecase/` | data | ui |
|---|---|---|---|
| F01 타이머 | `timer/`, `session/` | `repository/` | `session/` (SCR-02·03·04) |
| F02 저장·복원 | `session/` (체크포인트 모델) | `local/` | — |
| F03 배정·D0 | `translation/`, `allocation/` | — | `allocation/` (SCR-01), `correction/` (SCR-07) |
| F04·F17 UI·디자인 | — | — | `theme/`, `component/`, `navigation/` |
| F05 외부 음악 | `source/`, `link/` | `launcher/` | `music/` (SCR-06), `share/` (SCR-12) |
| F06 곡 세기 | `observation/` | `media/` + `service/` | `counting/` (SCR-11·13) |
| F07 재생 버튼·끝나면 멈추기 | `control/`, `stopatend/` | `media/` | `session/` (음악 패널) |
| F08 알림·알람 | `notification/` (문구·키 규칙) | `notification/`, `alarm/` + `receiver/` | — |
| F09 내장 소리 | `audio/` (페이드·볼륨 규칙) | `audio/` + `service/` | `music/` |
| F10 결과·메모·설정 | `result/`, `memo/`, `settings/` | `local/`, `repository/` | `result/` (SCR-05), `memo/` (SCR-08), `settings/` (SCR-09) |
| F12 분석 | `analytics/` (허용 필드) | `analytics/` (sink, 기본 OFF) | — |
| F18 세션 플레이리스트 | `playlist/` (`PlaylistEntry`·`SessionPlaylist`), `result/TotalTime` | `local/FilePlaylistStore` (`PlaylistRepository`) | `result/` (SCR-05 시안 A) |

- 새 패키지는 필요한 시점(첫 테스트 작성 시)에 만든다. 빈 패키지를 미리 만들지 않는다

## 부수효과·스레드

| 대상 | 규칙 |
|---|---|
| 타이머 부수효과 | `DispatchTimerCommandUseCase` → `TimerEffectSink`(= `CompositeTimerEffectSink`) → handler 집합: 알람(`EndAlarmScheduler`), 알림(`SessionNotifier`), 관측(`ObservationCoordinator`), 내장 소리(`NoiseEffectHandler`). handler 실패는 격리 (MT-T7) |
| 세션 쓰기 | `SessionRepository.withLock` 하나로 read-modify-write 직렬화 (UI·receiver·복원) |
| 관측·재생 버튼·끝나면 멈추기 | `ObservationCoordinator`의 모든 진입점은 `SerialRunner`로 **미디어 콜백 스레드 하나**에서 실행. `MediaObserver`는 그 스레드 전용, 잠금 없음 |
| 내장 소리 | `NoiseController` 제어는 메인, `render`만 오디오 스레드 (`@Volatile` 참조) |
| 알림 하나 | 진행 알림(`SessionNotifier`)을 `ProgressNotificationSlot`에 보관 → `NoisePlaybackService`가 같은 ID로 `startForeground`. 소리 정지 시 세션 중이면 DETACH, 아니면 REMOVE |
| 리스너 끊김 | `MediaAccessListener.onListenerDisconnected` → `requestRebind` 1회 + 관측 DEGRADED, 재연결 시 OBSERVING |
| 타이머↔음악 연동 (DEC-A16, Android 전용) | `TimerEffect.PauseLinkedMusic`/`ResumeLinkedMusic`은 사용자 일시정지·이어서 명령에서만 발생 (복원·복귀 0) → `ObservationCoordinator`가 재생 중이면 PAUSE, 이 앱이 멈춘 같은 곡이면 PLAY |
| 음악→타이머 (DEC-A17, 설정·기본 꺼짐) | 관측 음악이 진행 중 10초 넘게 멈추면 `ObservationCoordinator.onMusicPausedLong` → `TimerCommand.Pause`. 타이머가 RUNNING이 아니면(연동·끝나면 멈추기) 감시 안 함 |
| 늦은 알람 | 목표를 넘긴 곡 경계가 알람보다 먼저 오면 `ObservationCoordinator.onLimitDue` → `DispatchTimerCommandUseCase.tick()` (Hilt `Provider`로 순환 차단) |
| 재생 기록 (F18, DEC-A18) | `SongCounter.justCounted`(곡 수가 오른 전이) → `MediaObserver.setCounter` → `onSongCounted` → `ObservationCoordinator.onSongCounted(sessionId, entry)` → `ObservationWiring`이 `launch(UNDISPATCHED)`로 `PlaylistRepository.append`. 저장소 `Mutex`를 관측 스레드에서 먼저 잡아 순서 보존·마침 직후 결과 읽기가 마지막 곡을 기다림. 콜백·저장 실패 격리 (MT-T7) |
| 앨범 아트 (DEC-A21) | `MediaObserver.handleMetadata`가 곡이 현재가 될 때(또는 같은 곡 재게시로 아트가 늦게 올 때) `MediaSessionHandle.artwork(track)`으로 읽어 그 곡 키(패키지·제목·아티스트)와 함께 메모리 보관 → `setCounter`는 `justCounted` 키가 같을 때만 `PlaylistEntry.artwork`에 붙임(다음 곡 아트 0). `AndroidMediaSessionGateway`: 메타데이터 한 번 읽어 같은 곡일 때만 `ArtworkEncoder`(비트맵 ALBUM_ART→ART→DISPLAY_ICON, `*_URI` 금지, 120px 정사각·WebP/JPEG·24KB 상한). 실패 = 아트 없음 (MT-T7). 저장은 `FilePlaylistStore` 항목 안 base64(같은 수명). UI `CoverTile(artwork, …)`가 한 번 디코드, 없거나 못 읽으면 모티프. 공유 이미지 사용 금지 (`PrivacyRules.artworkSharers`) |
| 플레이리스트 보관 (DEC-A18) | `FilePlaylistStore`: `noBackupFilesDir/session_playlists.json` 하나, `AtomicFile`, 세션별 마지막 기록 시각. 손상 = 빈 목록. 삭제: 결과 닫기(`AppViewModel.onRestart`·`continueSameTask`), `DeleteAllDataUseCase`, 시작 시 `RestoreSessionUseCase`가 24시간(`SessionPlaylist.TTL_MS` = `RestorePolicy.RESULT_TTL_MS`) 지난 것 purge. 곡명·앨범 아트를 저장하는 유일한 곳 (`PrivacyRules.songPersisters`) |
| UI 갱신 | 진행 화면 1초 tick은 `repeatOnLifecycle(STARTED)` 안에서만 (화면 밖 폴링 0) |
- SRS §7.1 매니페스트 예시 경로 대응: `.observe.MediaAccessListener` → `.service.MediaAccessListener`, `.share.ShareReceiveActivity` → `.ui.share.ShareReceiveActivity`

## 명명

| 대상 | 규칙 | 예 |
|---|---|---|
| UseCase | 동사 + 목적어 + `UseCase` | `StartSessionUseCase` |
| Repository | `XxxRepository` (인터페이스) / `DefaultXxxRepository` (구현) | `SessionRepository` |
| 데이터 소스 | `XxxDataSource` | `CheckpointLocalDataSource` |
| 화면 | `XxxScreen` / `XxxViewModel` / `XxxUiState` | `SessionScreen` |
| 테스트 | 계층 접미사 (docs/testing.md) | `StartSessionUseCaseTest` |
