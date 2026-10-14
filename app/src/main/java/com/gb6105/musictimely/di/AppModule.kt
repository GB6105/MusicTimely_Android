package com.gb6105.musictimely.di

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.gb6105.musictimely.data.audio.AndroidAudioFocus
import com.gb6105.musictimely.data.audio.AndroidNoisePort
import com.gb6105.musictimely.data.audio.AudioTrackPcmSink
import com.gb6105.musictimely.data.audio.NoiseController
import com.gb6105.musictimely.data.audio.NoiseEffectHandler
import com.gb6105.musictimely.data.launcher.ExternalLauncher
import com.gb6105.musictimely.data.local.PreferencesSettingsRepository
import com.gb6105.musictimely.data.local.PreferencesSourceRepository
import com.gb6105.musictimely.data.media.AndroidCountingAccess
import com.gb6105.musictimely.data.media.AndroidMediaSessionGateway
import com.gb6105.musictimely.data.media.ObservationCoordinator
import com.gb6105.musictimely.data.media.connect
import com.gb6105.musictimely.domain.model.timer.TimerCommand
import com.gb6105.musictimely.domain.repository.CountingAccessPort
import com.gb6105.musictimely.domain.repository.MemoRepository
import com.gb6105.musictimely.domain.repository.MusicControlPort
import com.gb6105.musictimely.domain.repository.MusicLauncherPort
import com.gb6105.musictimely.domain.repository.NoisePort
import com.gb6105.musictimely.domain.repository.NotificationPermissionPort
import com.gb6105.musictimely.domain.repository.SettingsRepository
import com.gb6105.musictimely.domain.repository.ShareImagePort
import com.gb6105.musictimely.domain.repository.SourceRepository
import com.gb6105.musictimely.domain.usecase.settings.DeleteAllDataUseCase
import com.gb6105.musictimely.domain.usecase.source.RegisterSourceUseCase
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.gb6105.musictimely.data.alarm.EndAlarmScheduler
import com.gb6105.musictimely.data.effect.CompositeTimerEffectSink
import com.gb6105.musictimely.data.effect.TimerEffectHandler
import com.gb6105.musictimely.data.local.AndroidClock
import com.gb6105.musictimely.data.local.FileCheckpointStore
import com.gb6105.musictimely.data.local.FilePlaylistStore
import com.gb6105.musictimely.data.local.PreferencesMemoRepository
import com.gb6105.musictimely.data.notification.AndroidNotificationPermission
import com.gb6105.musictimely.data.notification.ProgressDismissal
import com.gb6105.musictimely.data.notification.ProgressNotificationSlot
import com.gb6105.musictimely.data.notification.SessionNotifier
import com.gb6105.musictimely.data.repository.DefaultSessionRepository
import com.gb6105.musictimely.data.shareimage.AndroidShareImagePort
import com.gb6105.musictimely.ui.shareimage.ShareImageRenderer
import com.gb6105.musictimely.domain.repository.CheckpointStore
import com.gb6105.musictimely.domain.repository.ClockPort
import com.gb6105.musictimely.domain.repository.PlaylistRepository
import com.gb6105.musictimely.domain.repository.SessionRepository
import com.gb6105.musictimely.domain.repository.TimerEffectSink
import com.gb6105.musictimely.domain.usecase.session.DispatchTimerCommandUseCase
import com.gb6105.musictimely.domain.usecase.session.OpenSessionUseCase
import com.gb6105.musictimely.domain.usecase.session.ReconcileSessionUseCase
import com.gb6105.musictimely.domain.usecase.session.RestoreSessionUseCase
import com.gb6105.musictimely.receiver.SessionActionHandler
import dagger.Module
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import java.util.UUID
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    /** Effect handlers contribute with `@IntoSet`; an empty set is valid. */
    @Multibinds
    abstract fun timerEffectHandlers(): Set<TimerEffectHandler>

    companion object {
        @Provides
        @Singleton
        fun clock(@ApplicationContext context: Context): ClockPort = AndroidClock(context)

        @Provides
        @Singleton
        fun checkpointStore(@ApplicationContext context: Context): CheckpointStore = FileCheckpointStore(context)

        @Provides
        @Singleton
        fun sessionRepository(store: CheckpointStore, clock: ClockPort): SessionRepository =
            DefaultSessionRepository(store, clock)

        @Provides
        @Singleton
        fun timerEffectSink(handlers: Set<@JvmSuppressWildcards TimerEffectHandler>): TimerEffectSink =
            CompositeTimerEffectSink(handlers)

        @Provides
        fun openSession(repository: SessionRepository) = OpenSessionUseCase(repository) { UUID.randomUUID().toString() }

        @Provides
        fun dispatchTimerCommand(repository: SessionRepository, clock: ClockPort, sink: TimerEffectSink) =
            DispatchTimerCommandUseCase(repository, clock, sink)

        @Provides
        fun restoreSession(store: CheckpointStore, clock: ClockPort, playlists: PlaylistRepository) =
            RestoreSessionUseCase(store, clock, playlists)

        @Provides
        fun reconcileSession(repository: SessionRepository, clock: ClockPort, sink: TimerEffectSink) =
            ReconcileSessionUseCase(repository, clock, sink)

        @Provides
        @Singleton
        fun progressDismissal() = ProgressDismissal()

        @Provides
        fun sessionActionHandler(dispatch: DispatchTimerCommandUseCase, dismissal: ProgressDismissal) = SessionActionHandler(dispatch, dismissal)

        @Provides
        @Singleton
        fun settingsRepository(@ApplicationContext context: Context): SettingsRepository = PreferencesSettingsRepository(context)

        @Provides
        @Singleton
        fun sourceRepository(@ApplicationContext context: Context): SourceRepository = PreferencesSourceRepository(context)

        @Provides
        fun externalLauncher(@ApplicationContext context: Context): MusicLauncherPort = ExternalLauncher(context)

        @Provides
        fun registerSource(repository: SourceRepository, clock: ClockPort) =
            RegisterSourceUseCase(repository, clock) { UUID.randomUUID().toString() }

        @Provides
        @Singleton
        fun observationCoordinator(
            @ApplicationContext context: Context,
            clock: ClockPort,
            settings: SettingsRepository,
            sources: SourceRepository,
            sessions: SessionRepository,
            playlists: PlaylistRepository,
            // Provider breaks the cycle sink → coordinator → dispatch → sink.
            dispatch: javax.inject.Provider<DispatchTimerCommandUseCase>,
        ): ObservationCoordinator {
            val handler = AndroidMediaSessionGateway.callbackHandler
            val coordinator = ObservationCoordinator(
                gateway = AndroidMediaSessionGateway(context, handler),
                clock = clock,
                ownPackage = context.packageName,
                settings = { settings.settings.value },
                preferredPackage = {
                    val selected = settings.settings.value.selectedSourceId
                    sources.sources.value.firstOrNull { it.id == selected }?.packageName
                },
                delayer = { delayMs, action ->
                    val runnable = Runnable(action)
                    handler.postDelayed(runnable, delayMs)
                    AutoCloseable { handler.removeCallbacks(runnable) }
                },
                serial = { block -> if (Looper.myLooper() == handler.looper) block() else handler.post(block) },
            )
            // Observation-side writes must never crash the process (MT-T7, review M5).
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, _ -> })
            coordinator.connect(
                scope, sessions, { dispatch.get().tick() }, settings, playlists,
                pauseTimer = { dispatch.get()(TimerCommand.Pause(UUID.randomUUID().toString())) },
            )
            return coordinator
        }

        @Provides
        @Singleton
        fun noiseController(@ApplicationContext context: Context, clock: ClockPort, settings: SettingsRepository): NoiseController {
            val main = Handler(Looper.getMainLooper())
            return NoiseController(
                sink = AudioTrackPcmSink(),
                focus = AndroidAudioFocus(context),
                clock = clock,
                schedule = { delayMs, action ->
                    val runnable = Runnable(action)
                    main.postDelayed(runnable, delayMs)
                    AutoCloseable { main.removeCallbacks(runnable) }
                },
                sound = { settings.settings.value.sound },
            )
        }

        @Provides
        fun noisePort(@ApplicationContext context: Context, controller: NoiseController): NoisePort = AndroidNoisePort(context, controller)

        @Provides
        @IntoSet
        fun noiseHandler(controller: NoiseController): TimerEffectHandler {
            val main = Handler(Looper.getMainLooper())
            return NoiseEffectHandler(controller) { block -> main.post(block) }
        }

        @Provides
        fun musicControl(coordinator: ObservationCoordinator): MusicControlPort = coordinator

        @Provides
        @Singleton
        fun memoRepository(@ApplicationContext context: Context, clock: ClockPort): MemoRepository =
            PreferencesMemoRepository(context, clock) { UUID.randomUUID().toString() }

        @Provides
        @Singleton
        fun playlistRepository(@ApplicationContext context: Context, clock: ClockPort): PlaylistRepository =
            FilePlaylistStore(context, clock)

        @Provides
        fun notificationPermission(@ApplicationContext context: Context): NotificationPermissionPort =
            AndroidNotificationPermission(context)

        @Provides
        fun countingAccess(@ApplicationContext context: Context): CountingAccessPort = AndroidCountingAccess(context)

        @Provides
        fun deleteAllData(
            store: CheckpointStore,
            settings: SettingsRepository,
            sources: SourceRepository,
            sink: TimerEffectSink,
            memos: MemoRepository,
            sessions: SessionRepository,
            playlists: PlaylistRepository,
            shareImages: ShareImagePort,
        ) = DeleteAllDataUseCase(store, settings, sources, sink, memos, sessions, playlists, shareImages)

        /** F18 M5–M7 (DEC-A22): the share image is drawn by the UI renderer, kept and sent by the data layer. */
        @Provides
        fun shareImagePort(@ApplicationContext context: Context): ShareImagePort =
            AndroidShareImagePort(context, render = ShareImageRenderer(context)::render)

        @Provides
        @IntoSet
        fun observationHandler(coordinator: ObservationCoordinator): TimerEffectHandler = coordinator

        @Provides
        @IntoSet
        fun endAlarmHandler(@ApplicationContext context: Context): TimerEffectHandler = EndAlarmScheduler(context)

        @Provides
        @IntoSet
        fun notificationHandler(
            @ApplicationContext context: Context,
            clock: ClockPort,
            dismissal: ProgressDismissal,
            slot: ProgressNotificationSlot,
            settings: SettingsRepository,
        ): TimerEffectHandler = SessionNotifier(
            context, clock, dismissal,
            isAppInForeground = { ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) },
            slot = slot,
            showSongs = { settings.settings.value.showSongs },
        )

        @Provides
        @Singleton
        fun progressNotificationSlot() = ProgressNotificationSlot()
    }
}
