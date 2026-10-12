package com.gb6105.musictimely.data.audio

import com.gb6105.musictimely.domain.model.audio.NoiseSound
import com.gb6105.musictimely.domain.model.audio.NoiseKind
import com.gb6105.musictimely.domain.repository.NoiseState
import com.gb6105.musictimely.testing.FakeClock
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.abs

/**
 * F09 내장 소리 제어 (FR-A055~A058, SRS v1 FR-027~030), QA-22·QA-23.
 * Sound starts only from an explicit user tap; interruptions stop it with no automatic resume.
 */
class NoiseControllerTest {

    private class FakeSink : PcmSink {
        var running = false
        var starts = 0
        var failNext = false
        override fun start(render: (FloatArray) -> Unit) {
            if (failNext) {
                failNext = false
                throw IllegalStateException("audio device busy")
            }
            running = true
            starts++
        }
        override fun stop() {
            running = false
        }
    }

    private class FakeFocus(var grant: Boolean = true) : AudioFocusPort {
        var held = false
        var onLoss: (() -> Unit)? = null
        override fun request(keepOthers: Boolean, onLoss: () -> Unit): Boolean {
            this.onLoss = onLoss
            held = grant
            return grant
        }
        override fun abandon() {
            held = false
        }
    }

    private class ManualDelayer(private val clock: FakeClock) {
        val tasks = mutableListOf<Pair<Long, () -> Unit>>()
        fun schedule(delayMs: Long, action: () -> Unit): AutoCloseable {
            val task = (clock.monotonicMs + delayMs) to action
            tasks += task
            return AutoCloseable { tasks -= task }
        }
        fun runDue() = tasks.filter { it.first <= clock.monotonicMs }.forEach { tasks -= it; it.second() }
    }

    private val clock = FakeClock()
    private val sink = FakeSink()
    private val focus = FakeFocus()
    private val delays = ManualDelayer(clock)
    private val controller = NoiseController(sink, focus, clock, { d, a -> delays.schedule(d, a) }, seed = 1)

    private fun level(): Float {
        val buffer = FloatArray(4_096)
        controller.render(buffer)
        return buffer.map { abs(it) }.average().toFloat()
    }

    @Test
    fun `nothing plays until the user taps play (FR-A056)`() {
        assertThat(sink.starts).isEqualTo(0)
        assertThat(controller.state.value.playing).isFalse()
    }

    @Test
    fun `play starts the sink with focus and fades in (FR-A057)`() {
        controller.play(NoiseKind.PINK)

        assertThat(sink.running).isTrue()
        assertThat(focus.held).isTrue()
        assertThat(controller.state.value).isEqualTo(NoiseState(kind = NoiseKind.PINK, playing = true))
        assertThat(level()).isEqualTo(0f)
        clock.advance(1_500)
        assertThat(level()).isGreaterThan(0f)
    }

    @Test
    fun `denied audio focus plays nothing (AUDIO_UNAVAILABLE)`() {
        focus.grant = false

        controller.play(NoiseKind.WHITE)

        assertThat(sink.running).isFalse()
        assertThat(controller.state.value.playing).isFalse()
        assertThat(controller.state.value.unavailable).isTrue()
    }

    @Test
    fun `timer pause ducks to 30 percent and resume restores (FR-A057)`() {
        controller.play(NoiseKind.WHITE)
        clock.advance(2_000)
        val full = level()

        controller.onTimerPaused()
        clock.advance(1_000)
        val ducked = level()
        controller.onTimerResumed()
        clock.advance(1_000)

        assertThat(ducked / full).isWithin(0.08f).of(0.3f)
        assertThat(level() / full).isWithin(0.08f).of(1f)
    }

    @Test
    fun `limit or finish fades out over 3 seconds then stops and releases focus (FR-A057, QA-22)`() {
        controller.play(NoiseKind.PINK)
        clock.advance(2_000)

        controller.onTimerEnded()
        assertThat(sink.running).isTrue()
        clock.advance(3_000)
        delays.runDue()

        assertThat(sink.running).isFalse()
        assertThat(focus.held).isFalse()
        assertThat(controller.state.value.playing).isFalse()
    }

    @Test
    fun `headphones unplugged or focus lost stops the sound with no automatic resume (FR-A058, QA-23)`() {
        controller.play(NoiseKind.PINK)
        controller.onBecomingNoisy()
        assertThat(sink.running).isFalse()

        controller.play(NoiseKind.PINK)
        focus.onLoss?.invoke()
        assertThat(sink.running).isFalse()
        assertThat(sink.starts).isEqualTo(2)
    }

    @Test
    fun `stop is immediate and keeps the chosen kind`() {
        controller.play(NoiseKind.WHITE)

        controller.stop()

        assertThat(sink.running).isFalse()
        assertThat(controller.state.value).isEqualTo(NoiseState(kind = NoiseKind.WHITE, playing = false))
    }

    @Test
    fun `starting while the timer is paused starts ducked (review 2 L3)`() {
        controller.play(NoiseKind.WHITE)
        clock.advance(2_000)
        val full = level()
        controller.stop()

        controller.onTimerPaused()
        controller.play(NoiseKind.WHITE)
        clock.advance(2_000)

        assertThat(level() / full).isWithin(0.08f).of(0.3f)
    }

    @Test
    fun `extending during the fade-out cancels the pending stop (review 2 L4)`() {
        controller.play(NoiseKind.PINK)
        clock.advance(2_000)
        controller.onTimerEnded()
        clock.advance(1_000)

        controller.onTimerResumed()
        clock.advance(3_000)
        delays.runDue()

        assertThat(sink.running).isTrue()
        assertThat(level()).isGreaterThan(0f)
    }

    @Test
    fun `an audio device failure releases focus and reports unavailable (review 2 L5)`() {
        sink.failNext = true

        controller.play(NoiseKind.PINK)

        assertThat(controller.state.value.unavailable).isTrue()
        assertThat(controller.state.value.playing).isFalse()
        assertThat(focus.held).isFalse()
    }

    @Test
    fun `the set volume and fade lengths are used (FR-A070)`() {
        val custom = NoiseController(sink, focus, clock, { d, a -> delays.schedule(d, a) }, seed = 1) {
            NoiseSound(volume = 0.1f, fadeInMs = 0, fadeOutMs = 10_000)
        }
        custom.play(NoiseKind.PINK)
        val buffer = FloatArray(4_096).also(custom::render)
        assertThat(buffer.maxOf { kotlin.math.abs(it) }).isAtMost(0.1f)
        assertThat(buffer.any { it != 0f }).isTrue()

        custom.onTimerEnded()
        clock.advance(3_000)
        delays.runDue()
        assertThat(sink.running).isTrue()
        clock.advance(7_000)
        delays.runDue()
        assertThat(sink.running).isFalse()
    }

    @Test
    fun `at zero volume nothing plays, so there is no silent playback (SRS Android 7_6)`() {
        val muted = NoiseController(sink, focus, clock, { d, a -> delays.schedule(d, a) }, seed = 1) { NoiseSound(volume = 0f) }

        muted.play(NoiseKind.WHITE)

        assertThat(sink.starts).isEqualTo(0)
        assertThat(focus.held).isFalse()
        assertThat(muted.state.value).isEqualTo(NoiseState(kind = NoiseKind.WHITE, playing = false, muted = true))
    }

    @Test
    fun `stopping clears an earlier muted or unavailable notice (review L7)`() {
        focus.grant = false
        controller.play(NoiseKind.PINK)
        assertThat(controller.state.value.unavailable).isTrue()

        controller.stop()

        assertThat(controller.state.value).isEqualTo(NoiseState(kind = NoiseKind.PINK, playing = false))
    }
}
