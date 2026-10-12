package com.gb6105.musictimely.data.audio

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gb6105.musictimely.domain.model.audio.NoiseKind
import com.gb6105.musictimely.data.notification.SessionNotifier
import com.gb6105.musictimely.service.NoisePlaybackService
import com.gb6105.musictimely.testing.FakeClock
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

/** mediaPlayback FGS는 내장 소리 재생 중에만 (SRS Android §7.6), 헤드폰 분리 정지 (FR-A058), 포커스 (FR-A057). */
@RunWith(AndroidJUnit4::class)
class NoisePlaybackServiceIntegrationTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun grant() = shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

    @Test
    fun `the service is declared as a non-exported mediaPlayback foreground service`() {
        val info = app.packageManager.getServiceInfo(
            android.content.ComponentName(app, NoisePlaybackService::class.java), PackageManager.GET_META_DATA,
        )

        assertThat(info.exported).isFalse()
        assertThat(info.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK).isNotEqualTo(0)
        val requested = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertThat(requested.toList()).containsAtLeast(Manifest.permission.FOREGROUND_SERVICE, Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK)
    }

    @Test
    fun `while noise plays the service stays in the foreground with a notification`() {
        val built = Robolectric.buildService(NoisePlaybackService::class.java, Intent()).create()
        built.get().controller.play(NoiseKind.PINK)

        built.startCommand(0, 1)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertThat(shadowOf(built.get()).lastForegroundNotification).isNotNull()
        assertThat(shadowOf(built.get()).isStoppedBySelf).isFalse()
        built.get().controller.stop()
    }

    @Test
    fun `audio focus is requested for media and its loss stops the sound`() {
        val focus = AndroidAudioFocus(app)
        var lost = false

        assertThat(focus.request { lost = true }).isTrue()
        val request = shadowOf(app.getSystemService(AudioManager::class.java)).lastAudioFocusRequest
        request.listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)

        assertThat(lost).isTrue()
    }

    @Test
    fun `an AudioTrack sink starts and stops cleanly`() {
        val sink = AudioTrackPcmSink()

        sink.start { it.fill(0f) }
        sink.stop()
        sink.stop()

        assertThat(sink.isRunning).isFalse()
    }

    @Test
    fun `the noise port starts the service on play`() {
        val clock = FakeClock()
        val port = AndroidNoisePort(app, NoiseController(AudioTrackPcmSink(), AndroidAudioFocus(app), clock, { _, _ -> AutoCloseable {} }))

        port.play(NoiseKind.PINK)

        assertThat(shadowOf(app).nextStartedService?.component?.className).isEqualTo(NoisePlaybackService::class.java.name)
        assertThat(port.state.value.playing).isTrue()
        port.stop()
    }

    @Test
    fun `with nothing playing the service still goes foreground first, then stops itself (start-order safety)`() {
        val service = Robolectric.buildService(NoisePlaybackService::class.java, Intent()).create().startCommand(0, 1).get()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertThat(shadowOf(service).isForegroundStopped).isTrue()
        assertThat(shadowOf(service).isStoppedBySelf).isTrue()
    }

    @Test
    fun `keeping the other app's music asks only for duckable transient focus (review 2 M8)`() {
        val focus = AndroidAudioFocus(app)

        focus.request(keepOthers = true) {}
        assertThat(shadowOf(app.getSystemService(AudioManager::class.java)).lastAudioFocusRequest.durationHint)
            .isEqualTo(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)

        focus.request(keepOthers = false) {}
        assertThat(shadowOf(app.getSystemService(AudioManager::class.java)).lastAudioFocusRequest.durationHint)
            .isEqualTo(AudioManager.AUDIOFOCUS_GAIN)
    }

    @Test
    fun `noise rides on the session progress notification instead of posting a second one (SRS Android 7_6)`() {
        val built = Robolectric.buildService(NoisePlaybackService::class.java, Intent()).create()
        val progress = androidx.core.app.NotificationCompat.Builder(app, SessionNotifier.CHANNEL_PROGRESS)
            .setSmallIcon(com.gb6105.musictimely.R.drawable.ic_stat_session).setContentTitle("진행").build()
        built.get().progressSlot.setForTest(progress)
        built.get().controller.play(NoiseKind.PINK)

        built.startCommand(0, 1)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertThat(shadowOf(built.get()).lastForegroundNotificationId).isEqualTo(SessionNotifier.ID_PROGRESS)
        assertThat(shadowOf(built.get()).lastForegroundNotification).isSameInstanceAs(progress)

        built.get().controller.stop()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        // Mid-session the progress notification stays when the sound stops.
        assertThat(shadowOf(built.get()).isForegroundStopped).isTrue()
        assertThat(shadowOf(built.get()).notificationShouldRemoved).isFalse()
        built.get().progressSlot.setForTest(null)
    }

    @Test
    fun `without a session the sound notification uses the same id and is removed when the sound stops`() {
        val built = Robolectric.buildService(NoisePlaybackService::class.java, Intent()).create()
        built.get().progressSlot.setForTest(null)
        built.get().controller.play(NoiseKind.PINK)
        built.startCommand(0, 1)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertThat(shadowOf(built.get()).lastForegroundNotificationId).isEqualTo(SessionNotifier.ID_PROGRESS)

        built.get().controller.stop()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertThat(shadowOf(built.get()).notificationShouldRemoved).isTrue()
    }

    @Test
    fun `a second play on the same service instance is watched again and released when it stops (review L6)`() {
        val built = Robolectric.buildService(NoisePlaybackService::class.java, Intent()).create()
        built.get().progressSlot.setForTest(null)
        val controller = built.get().controller
        controller.play(NoiseKind.PINK)
        built.startCommand(0, 1)
        controller.stop()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        controller.play(NoiseKind.WHITE)
        built.startCommand(0, 2)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertThat(shadowOf(built.get()).isForegroundStopped).isFalse()

        controller.stop()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertThat(shadowOf(built.get()).isForegroundStopped).isTrue()
    }
}
