package com.gb6105.musictimely.domain.model.audio

import java.util.SplittableRandom

enum class NoiseKind { WHITE, PINK }

/**
 * Procedural noise (FR-A055): no recorded asset, works offline. Output is hard-limited to [CEILING]
 * so the set volume is the loudest the user hears (SRS v1 FR-027 limiter). Noise is never counted as songs.
 */
class NoiseGenerator(private val kind: NoiseKind, seed: Long = System.nanoTime()) {
    private val random = SplittableRandom(seed)
    private var b0 = 0.0
    private var b1 = 0.0
    private var b2 = 0.0

    fun fill(buffer: FloatArray) {
        for (i in buffer.indices) {
            val white = random.nextDouble(-1.0, 1.0)
            val sample = when (kind) {
                NoiseKind.WHITE -> white * WHITE_SCALE
                NoiseKind.PINK -> {
                    // Paul Kellet's economy pink filter (−3 dB/octave).
                    b0 = 0.99765 * b0 + white * 0.0990460
                    b1 = 0.96300 * b1 + white * 0.2965164
                    b2 = 0.57000 * b2 + white * 1.0526913
                    (b0 + b1 + b2 + white * 0.1848) * PINK_SCALE
                }
            }
            buffer[i] = sample.toFloat().coerceIn(-CEILING, CEILING)
        }
    }

    companion object {
        const val CEILING = 0.9f
        private const val WHITE_SCALE = 0.5
        private const val PINK_SCALE = 0.2
    }
}

/**
 * Gain over time for app-owned sound (FR-A057): 1.5 s fade-in at start, duck to 30 % while the timer is paused,
 * 3 s fade-out at finish or limit. Each change ramps linearly from the current gain.
 */
data class NoiseEnvelope(
    val volume: Float,
    private val fromGain: Float,
    private val toGain: Float,
    private val startMs: Long,
    private val durationMs: Long,
    private val fadingOut: Boolean,
    private val fadeOutMs: Long = DEFAULT_FADE_OUT_MS,
) {
    fun gainAt(nowMs: Long): Float {
        if (durationMs <= 0 || nowMs >= startMs + durationMs) return toGain
        if (nowMs <= startMs) return fromGain
        val progress = (nowMs - startMs).toFloat() / durationMs
        return fromGain + (toGain - fromGain) * progress
    }

    fun duck(atMs: Long) = rampTo(volume * DUCK_RATIO, atMs, DUCK_MS, fadingOut = false)

    fun restore(atMs: Long) = rampTo(volume, atMs, DUCK_MS, fadingOut = false)

    fun fadeOut(atMs: Long) = rampTo(0f, atMs, fadeOutMs, fadingOut = true)

    /** How long [fadeOut] takes, for scheduling the stop. */
    val fadeOutDurationMs: Long get() = fadeOutMs

    fun isSilentAt(nowMs: Long): Boolean = fadingOut && nowMs >= startMs + durationMs

    private fun rampTo(target: Float, atMs: Long, duration: Long, fadingOut: Boolean) =
        copy(fromGain = gainAt(atMs), toGain = target, startMs = atMs, durationMs = duration, fadingOut = fadingOut)

    companion object {
        const val DEFAULT_VOLUME = 0.2f
        const val DUCK_MS = 300L
        const val DEFAULT_FADE_IN_MS = 1_500L
        const val DEFAULT_FADE_OUT_MS = 3_000L
        private const val DUCK_RATIO = 0.3f

        fun start(volume: Float, atMs: Long, fadeInMs: Long = DEFAULT_FADE_IN_MS, fadeOutMs: Long = DEFAULT_FADE_OUT_MS): NoiseEnvelope {
            val v = volume.coerceIn(0f, 1f)
            return NoiseEnvelope(v, fromGain = 0f, toGain = v, startMs = atMs, durationMs = fadeInMs, fadingOut = false, fadeOutMs = fadeOutMs)
        }
    }
}

/** FR-A070 sound settings: volume 0..1, fade-in 0..5 s, fade-out 0..10 s. */
data class NoiseSound(
    val volume: Float = NoiseEnvelope.DEFAULT_VOLUME,
    val fadeInMs: Long = NoiseEnvelope.DEFAULT_FADE_IN_MS,
    val fadeOutMs: Long = NoiseEnvelope.DEFAULT_FADE_OUT_MS,
) {
    companion object {
        private val FADE_IN_RANGE = 0L..5_000L
        private val FADE_OUT_RANGE = 0L..10_000L

        fun isValidVolume(volume: Float): Boolean = volume in 0f..1f
        fun isValidFadeIn(ms: Long): Boolean = ms in FADE_IN_RANGE
        fun isValidFadeOut(ms: Long): Boolean = ms in FADE_OUT_RANGE
    }
}
