package com.gb6105.musictimely.data.media

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gb6105.musictimely.domain.model.observation.TrackMetadata
import com.gb6105.musictimely.service.MediaAccessListener
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper

/** F06 세션 연결 (SRS Android §7.2). FR-A030·A033. 명령 전송은 실기기 *DeviceTest 범위. */
@RunWith(AndroidJUnit4::class)
class AndroidMediaSessionGatewayIntegrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val gateway = AndroidMediaSessionGateway(context, Handler(Looper.getMainLooper()))
    private val manager = context.getSystemService(MediaSessionManager::class.java)

    private fun controller(pkg: String, title: String?, playing: Boolean): MediaController {
        val controller = MediaController(context, MediaSession(context, pkg).sessionToken)
        shadowOf(controller).setPackageName(pkg)
        val metadata = MediaMetadata.Builder().apply {
            title?.let { putString(MediaMetadata.METADATA_KEY_TITLE, it) }
            putString(MediaMetadata.METADATA_KEY_ARTIST, "LUMEN FIELD")
            putLong(MediaMetadata.METADATA_KEY_DURATION, 200_000)
        }.build()
        shadowOf(controller).setMetadata(metadata)
        shadowOf(controller).setPlaybackState(
            PlaybackState.Builder().setState(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, 42_000, 1f).build(),
        )
        shadowOf(manager).addController(controller)
        return controller
    }

    @Test
    fun `access is granted only when this app's listener is enabled (FR-A030)`() {
        assertThat(gateway.isAccessGranted()).isFalse()

        shadowOf(context.getSystemService(android.app.NotificationManager::class.java))
            .setNotificationListenerAccessGranted(android.content.ComponentName(context, MediaAccessListener::class.java), true)

        assertThat(gateway.isAccessGranted()).isTrue()
    }

    @Test
    fun `reads package, playback, metadata and position of active sessions`() {
        controller("com.spotify.music", "Slow Tide", playing = true)

        val session = gateway.activeSessions().single()

        assertThat(session.packageName).isEqualTo("com.spotify.music")
        assertThat(session.isPlaying()).isTrue()
        assertThat(session.metadata()).isEqualTo(TrackMetadata("com.spotify.music", "Slow Tide", "LUMEN FIELD", 200_000))
        assertThat(session.positionMs()).isEqualTo(42_000)
    }

    @Test
    fun `a session without a title has no title`() {
        controller("com.iloen.melon", title = null, playing = false)

        val session = gateway.activeSessions().single()

        assertThat(session.isPlaying()).isFalse()
        assertThat(session.metadata()?.title).isNull()
    }

    @Test
    fun `watching forwards metadata and playback changes until closed`() {
        val controller = controller("com.spotify.music", "a", playing = true)
        val session = gateway.activeSessions().single()
        var metadata = 0
        var playback = 0
        val watch = session.watch(object : MediaSessionListener {
            override fun onMetadataChanged() { metadata++ }
            override fun onPlaybackChanged() { playback++ }
            override fun onDestroyed() = Unit
        })

        shadowOf(controller).executeOnMetadataChanged(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "b").build())
        shadowOf(controller).executeOnPlaybackStateChanged(PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED, 0, 1f).build())
        ShadowLooper.idleMainLooper()
        watch.close()

        assertThat(metadata).isEqualTo(1)
        assertThat(playback).isEqualTo(1)
        assertThat(shadowOf(controller).callbacks).isEmpty()
    }

    @Test
    fun `transitional states such as buffering and skipping count as playing (review MEDIUM-10)`() {
        val controller = controller("com.spotify.music", "a", playing = true)
        val session = gateway.activeSessions().single()

        listOf(PlaybackState.STATE_BUFFERING, PlaybackState.STATE_SKIPPING_TO_NEXT, PlaybackState.STATE_CONNECTING).forEach { state ->
            shadowOf(controller).setPlaybackState(PlaybackState.Builder().setState(state, 0, 1f).build())
            assertThat(session.isPlaying()).isTrue()
        }
        shadowOf(controller).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_STOPPED, 0, 1f).build())
        assertThat(session.isPlaying()).isFalse()
    }
}

