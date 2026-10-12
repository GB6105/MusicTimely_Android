package com.gb6105.musictimely.data.audio

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import androidx.core.content.ContextCompat
import com.gb6105.musictimely.data.effect.TimerEffectHandler
import com.gb6105.musictimely.domain.model.audio.NoiseKind
import com.gb6105.musictimely.domain.model.timer.TimerEffect
import com.gb6105.musictimely.domain.model.timer.TimerSession
import com.gb6105.musictimely.domain.model.timer.TimerState
import com.gb6105.musictimely.domain.repository.NoisePort
import com.gb6105.musictimely.domain.repository.NoiseState
import com.gb6105.musictimely.service.NoisePlaybackService
import kotlinx.coroutines.flow.StateFlow

interface NoiseTimerHooks {
    fun onTimerPaused()
    fun onTimerResumed()
    fun onTimerEnded()
}

/**
 * SRS v1 §5.1 side effects on app-owned sound: pause ducks, resume restores, limit and finish fade out.
 * Effects may arrive on the receiver's thread; the controller is driven on main only (review 2 M5).
 */
class NoiseEffectHandler(
    private val hooks: NoiseTimerHooks,
    private val onMain: (() -> Unit) -> Unit,
) : TimerEffectHandler {
    override fun handles(effect: TimerEffect): Boolean =
        effect == TimerEffect.ShowProgress || effect == TimerEffect.ShowLimitReached || effect == TimerEffect.ClearNotifications

    override suspend fun handle(session: TimerSession, effect: TimerEffect) {
        when (effect) {
            TimerEffect.ShowProgress -> when (session.state) {
                TimerState.PAUSED -> onMain(hooks::onTimerPaused)
                TimerState.RUNNING -> onMain(hooks::onTimerResumed)
                else -> Unit
            }
            TimerEffect.ShowLimitReached, TimerEffect.ClearNotifications -> onMain(hooks::onTimerEnded)
            else -> Unit
        }
    }
}

/** 44.1 kHz mono float PCM on its own thread. */
class AudioTrackPcmSink : PcmSink {
    @Volatile private var running = false
    private var thread: Thread? = null
    private var track: AudioTrack? = null

    val isRunning: Boolean get() = running

    override fun start(render: (FloatArray) -> Unit) {
        stop()
        val minBytes = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(MEDIA_ATTRIBUTES)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minBytes, FRAMES * Float.SIZE_BYTES))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = audioTrack
        running = true
        audioTrack.play()
        thread = Thread({
            val buffer = FloatArray(FRAMES)
            while (running) {
                render(buffer)
                if (audioTrack.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING) < 0) break
            }
        }, "noise-render").apply { start() }
    }

    override fun stop() {
        running = false
        thread?.join(JOIN_TIMEOUT_MS)
        thread = null
        track?.let {
            runCatching { it.stop() }
            it.release()
        }
        track = null
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val FRAMES = 2_048
        const val JOIN_TIMEOUT_MS = 500L
    }
}

/** Media audio focus. The app never ducks other apps for its own notifications (DEC-09). */
class AndroidAudioFocus(private val context: Context) : AudioFocusPort {
    private val manager get() = context.getSystemService(AudioManager::class.java)
    private var request: AudioFocusRequest? = null

    override fun request(keepOthers: Boolean, onLoss: () -> Unit): Boolean {
        // [그대로] keeps the other app playing: duckable transient focus instead of taking it over (review 2 M8).
        val hint = if (keepOthers) AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK else AudioManager.AUDIOFOCUS_GAIN
        val focusRequest = AudioFocusRequest.Builder(hint)
            .setAudioAttributes(MEDIA_ATTRIBUTES)
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) onLoss()
            }
            .build()
        request = focusRequest
        return manager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    override fun abandon() {
        request?.let { manager.abandonAudioFocusRequest(it) }
        request = null
    }
}

/** Starts the mediaPlayback foreground service only while app sound plays (SRS Android §7.6). */
class AndroidNoisePort(private val context: Context, private val controller: NoiseController) : NoisePort {
    override val state: StateFlow<NoiseState> get() = controller.state

    override fun play(kind: NoiseKind, keepOtherMusic: Boolean) {
        controller.play(kind, keepOtherMusic)
        if (controller.state.value.playing) {
            ContextCompat.startForegroundService(context, Intent(context, NoisePlaybackService::class.java))
        }
    }

    override fun stop() = controller.stop()
}

private val MEDIA_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_MEDIA)
    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
    .build()
