package com.gb6105.musictimely.data.audio

import com.gb6105.musictimely.domain.model.audio.NoiseSound
import com.gb6105.musictimely.domain.model.audio.NoiseEnvelope
import com.gb6105.musictimely.domain.model.audio.NoiseGenerator
import com.gb6105.musictimely.domain.model.audio.NoiseKind
import com.gb6105.musictimely.domain.repository.ClockPort
import com.gb6105.musictimely.domain.repository.NoiseState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** PCM output (AudioTrack in production). [start] pulls buffers through the render callback on its own thread. */
interface PcmSink {
    fun start(render: (FloatArray) -> Unit)
    fun stop()
}

interface AudioFocusPort {
    /** @return false when focus is denied; [onLoss] runs when another app takes focus. */
    fun request(keepOthers: Boolean = false, onLoss: () -> Unit): Boolean
    fun abandon()
}

/**
 * App-owned noise (F09). Starts only from an explicit user action (FR-A056); fades and ducks with the timer
 * (FR-A057); headphone unplug or a call stops it with no automatic resume (FR-A058).
 * [render] runs on the audio thread, everything else on the main thread.
 */
class NoiseController(
    private val sink: PcmSink,
    private val focus: AudioFocusPort,
    private val clock: ClockPort,
    private val schedule: (Long, () -> Unit) -> AutoCloseable,
    private val seed: Long = System.nanoTime(),
    private val sound: () -> NoiseSound = { NoiseSound() },
) : NoiseTimerHooks {
    private val _state = MutableStateFlow(NoiseState())
    val state: StateFlow<NoiseState> = _state

    @Volatile private var envelope: NoiseEnvelope? = null
    @Volatile private var generator: NoiseGenerator? = null
    private var stopTask: AutoCloseable? = null
    private var timerPaused = false

    fun play(kind: NoiseKind, keepOtherMusic: Boolean = false) {
        release()
        val s = sound()
        if (s.volume <= 0f) {
            // No silent playback: nothing starts, no focus, no service (SRS Android §7.6).
            _state.value = NoiseState(kind = kind, playing = false, muted = true)
            return
        }
        if (!focus.request(keepOtherMusic, onLoss = ::stop)) {
            _state.value = NoiseState(kind = kind, playing = false, unavailable = true)
            return
        }
        val now = clock.monotonicNowMs()
        generator = NoiseGenerator(kind, seed)
        // Started while the timer rests: begin ducked (review 2 L3).
        envelope = NoiseEnvelope.start(s.volume, now, s.fadeInMs, s.fadeOutMs).let { if (timerPaused) it.duck(now) else it }
        try {
            sink.start(::render)
        } catch (_: RuntimeException) {
            // AUDIO_UNAVAILABLE: no sound, focus returned, the timer is unaffected (review 2 L5).
            release()
            _state.value = NoiseState(kind = kind, playing = false, unavailable = true)
            return
        }
        _state.value = NoiseState(kind = kind, playing = true)
    }

    fun stop() {
        release()
        _state.value = NoiseState(kind = _state.value.kind, playing = false)
    }

    override fun onTimerPaused() {
        timerPaused = true
        envelope = envelope?.duck(clock.monotonicNowMs())
    }

    /** Resume or extension: back to the set volume; a fade-out in progress is cancelled (review 2 L4). */
    override fun onTimerResumed() {
        timerPaused = false
        stopTask?.close()
        stopTask = null
        envelope = envelope?.restore(clock.monotonicNowMs())
    }

    /** Finish or limit: 3 s fade-out, then stop and release focus. */
    override fun onTimerEnded() {
        timerPaused = false
        val current = envelope ?: return
        envelope = current.fadeOut(clock.monotonicNowMs())
        stopTask?.close()
        stopTask = schedule(current.fadeOutDurationMs, ::stop)
    }

    fun onBecomingNoisy() = stop()

    fun render(buffer: FloatArray) {
        val env = envelope
        val gen = generator
        if (env == null || gen == null) {
            buffer.fill(0f)
            return
        }
        gen.fill(buffer)
        val gain = env.gainAt(clock.monotonicNowMs())
        for (i in buffer.indices) buffer[i] *= gain
    }

    private fun release() {
        stopTask?.close()
        stopTask = null
        envelope = null
        generator = null
        sink.stop()
        focus.abandon()
    }
}
