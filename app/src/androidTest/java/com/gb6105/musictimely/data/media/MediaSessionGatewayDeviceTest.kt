package com.gb6105.musictimely.data.media

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.HandlerThread
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gb6105.musictimely.allowListener
import com.gb6105.musictimely.disallowListener
import com.gb6105.musictimely.isListenerAllowed
import com.gb6105.musictimely.restoreListener
import com.gb6105.musictimely.domain.model.control.MusicCommand
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real MediaSessionManager through this app's notification listener (SRS Android §7.2): a session published
 * on the device is seen with its metadata and playing state, callbacks arrive on the observation thread, and a
 * pause reaches that session. The session lives in this process, standing in for another music app.
 */
@RunWith(AndroidJUnit4::class)
class MediaSessionGatewayDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val thread = HandlerThread("test-observation").apply { start() }
    private val sessionThread = HandlerThread("test-music-app").apply { start() }
    private val gateway = AndroidMediaSessionGateway(context, Handler(thread.looper))
    private val paused = CountDownLatch(1)
    private val listenerWasAllowed = isListenerAllowed()
    private var session: MediaSession? = null

    @Before
    fun publishSession() {
        allowListener()
        session = MediaSession(context, "device-test").apply {
            setCallback(
                object : MediaSession.Callback() {
                    override fun onPause() = paused.countDown()
                },
                Handler(sessionThread.looper),
            )
            setMetadata(track("Slow Tide"))
            setPlaybackState(state(PlaybackState.STATE_PLAYING))
            isActive = true
        }
    }

    @After
    fun tearDown() {
        session?.release()
        thread.quitSafely()
        sessionThread.quitSafely()
        restoreListener(listenerWasAllowed)
    }

    @Test
    fun grantedAccessSeesThePlayingSessionWithItsMetadata() {
        assertThat(gateway.isAccessGranted()).isTrue()

        val handle = gateway.activeSessions().single { it.packageName == context.packageName }

        assertThat(handle.isPlaying()).isTrue()
        assertThat(handle.metadata()?.title).isEqualTo("Slow Tide")
        assertThat(handle.metadata()?.durationMs).isEqualTo(200_000)
    }

    @Test
    fun withoutListenerAccessSessionsCannotBeRead() {
        disallowListener()

        assertThat(gateway.isAccessGranted()).isFalse()
        val read = runCatching { gateway.activeSessions() }
        assertThat(read.exceptionOrNull()).isInstanceOf(SecurityException::class.java)
    }

    @Test
    fun trackAndPlaybackChangesArriveOnTheObservationThread() {
        val handle = gateway.activeSessions().single { it.packageName == context.packageName }
        val metadata = CountDownLatch(1)
        val playback = CountDownLatch(1)
        val threads = mutableSetOf<Thread>()
        val watch = handle.watch(object : MediaSessionListener {
            override fun onMetadataChanged() {
                threads += Thread.currentThread()
                metadata.countDown()
            }
            override fun onPlaybackChanged() {
                threads += Thread.currentThread()
                playback.countDown()
            }
            override fun onDestroyed() = Unit
        })

        session!!.setMetadata(track("Next Song"))
        session!!.setPlaybackState(state(PlaybackState.STATE_PAUSED))

        assertThat(metadata.await(5, TimeUnit.SECONDS)).isTrue()
        assertThat(playback.await(5, TimeUnit.SECONDS)).isTrue()
        assertThat(threads).containsExactly(thread)
        assertThat(handle.metadata()?.title).isEqualTo("Next Song")
        assertThat(handle.isPlaying()).isFalse()
        watch.close()
    }

    @Test
    fun aPauseCommandReachesTheObservedSession() {
        val handle = gateway.activeSessions().single { it.packageName == context.packageName }

        handle.send(MusicCommand.PAUSE)

        assertThat(paused.await(5, TimeUnit.SECONDS)).isTrue()
    }

    private fun track(title: String) = MediaMetadata.Builder()
        .putString(MediaMetadata.METADATA_KEY_TITLE, title)
        .putString(MediaMetadata.METADATA_KEY_ARTIST, "Artist")
        .putLong(MediaMetadata.METADATA_KEY_DURATION, 200_000)
        .build()

    private fun state(value: Int) = PlaybackState.Builder()
        .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE)
        .setState(value, 50_000, 1f)
        .build()
}
