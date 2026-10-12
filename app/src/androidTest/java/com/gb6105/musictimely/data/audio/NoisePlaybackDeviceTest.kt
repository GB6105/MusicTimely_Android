package com.gb6105.musictimely.data.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gb6105.musictimely.MainActivity
import com.gb6105.musictimely.appGraph
import com.gb6105.musictimely.domain.model.audio.NoiseKind
import com.gb6105.musictimely.eventually
import com.gb6105.musictimely.shell
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * App-owned sound on the real OS (SRS Android §7.6, FR-A056~A058): a user tap with the app on screen starts the
 * mediaPlayback foreground service and real audio; stopping ends both; another app taking focus stops it.
 */
@RunWith(AndroidJUnit4::class)
class NoisePlaybackDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val audio = context.getSystemService(AudioManager::class.java)
    private val noise = appGraph.noise()
    private lateinit var scenario: ActivityScenario<MainActivity>

    private fun serviceDump() = shell("dumpsys activity services com.gb6105.musictimely/.service.NoisePlaybackService")
    private fun serviceRunning() = "ServiceRecord" in serviceDump()

    @Before
    fun openApp() {
        shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun closeApp() {
        scenario.onActivity { noise.stop() }
        eventually { !serviceRunning() }
        scenario.close()
    }

    private fun play(kind: NoiseKind) = scenario.onActivity { noise.play(kind) }

    @Test
    fun playingStartsTheMediaPlaybackForegroundServiceAndRealAudio() {
        play(NoiseKind.PINK)

        assertThat(noise.state.value.playing).isTrue()
        // Foreground with type mediaPlayback (0x2), riding on the progress notification id (SRS Android §7.6).
        val foreground = Regex("""isForeground=true foregroundId=1 types=0x00000002""")
        assertThat(eventually { foreground.containsMatchIn(serviceDump()) }).isTrue()
        assertThat(eventually { audio.isMusicActive }).isTrue()
    }

    @Test
    fun stoppingEndsTheAudioAndTheService() {
        play(NoiseKind.WHITE)
        assertThat(eventually { serviceRunning() }).isTrue()

        scenario.onActivity { noise.stop() }

        assertThat(eventually { !serviceRunning() }).isTrue()
        assertThat(eventually { !audio.isMusicActive }).isTrue()
    }

    @Test
    fun anotherAppTakingAudioFocusStopsTheSoundWithoutResuming() {
        play(NoiseKind.PINK)
        assertThat(eventually { serviceRunning() }).isTrue()

        val other = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
            .build()
        audio.requestAudioFocus(other)
        try {
            assertThat(eventually { !noise.state.value.playing }).isTrue()
            assertThat(eventually { !serviceRunning() }).isTrue()
        } finally {
            audio.abandonAudioFocusRequest(other)
        }
        Thread.sleep(1_000)
        assertThat(noise.state.value.playing).isFalse()
    }
}
