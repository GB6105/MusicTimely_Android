package com.gb6105.musictimely.domain.model.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.abs

/** F09 내장 노이즈 (FR-A055, SRS v1 FR-027): 화이트·핑크, 오프라인 절차 생성, 출력 제한. */
class NoiseTest {

    private fun samples(kind: NoiseKind, n: Int = 48_000) = NoiseGenerator(kind, seed = 7).let { g -> FloatArray(n).also(g::fill) }

    @Test
    fun `both noises stay inside the limiter ceiling`() {
        NoiseKind.entries.forEach { kind ->
            assertThat(samples(kind).maxOf { abs(it) }).isAtMost(NoiseGenerator.CEILING)
        }
    }

    @Test
    fun `noise is centred and not silent`() {
        NoiseKind.entries.forEach { kind ->
            val s = samples(kind)
            assertThat(abs(s.average())).isLessThan(0.05)
            assertThat(s.map { abs(it) }.average()).isGreaterThan(0.01)
        }
    }

    @Test
    fun `pink noise has more low-frequency energy than white noise`() {
        // Ratio of signal power to first-difference power is higher for low-frequency-heavy signals.
        fun lowness(s: FloatArray): Double {
            val power = s.sumOf { (it * it).toDouble() }
            val diff = (1 until s.size).sumOf { ((s[it] - s[it - 1]) * (s[it] - s[it - 1])).toDouble() }
            return power / diff
        }
        assertThat(lowness(samples(NoiseKind.PINK))).isGreaterThan(lowness(samples(NoiseKind.WHITE)) * 2)
    }

    @Test
    fun `the same seed gives the same samples`() {
        assertThat(samples(NoiseKind.PINK, 256).toList()).isEqualTo(samples(NoiseKind.PINK, 256).toList())
    }
}
