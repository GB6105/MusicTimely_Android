package com.gb6105.musictimely.domain.model.result

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** F18 M3 총 걸린 시간: 일시정지 제외 활성 시간, 초 단위 내림. 시간이 있으면 시·분·초 모두. */
class TotalTimeTest {

    @Test
    fun `under a minute shows seconds only`() {
        assertThat(TotalTime.of(0)).isEqualTo(TotalTime(0, 0, 0))
        assertThat(TotalTime.of(0).shape).isEqualTo(TotalTime.Shape.SECONDS)
        assertThat(TotalTime.of(59_999)).isEqualTo(TotalTime(0, 0, 59))
        assertThat(TotalTime.of(59_999).shape).isEqualTo(TotalTime.Shape.SECONDS)
    }

    @Test
    fun `minutes and seconds below an hour (Figma 32분 18초)`() {
        val t = TotalTime.of(32 * 60_000L + 18_400)

        assertThat(t).isEqualTo(TotalTime(0, 32, 18))
        assertThat(t.shape).isEqualTo(TotalTime.Shape.MINUTES_SECONDS)
        assertThat(TotalTime.of(60_000).shape).isEqualTo(TotalTime.Shape.MINUTES_SECONDS)
    }

    @Test
    fun `an hour or more adds hours and keeps minutes and seconds`() {
        val t = TotalTime.of(3_600_000L + 5 * 60_000L + 3_000)

        assertThat(t).isEqualTo(TotalTime(1, 5, 3))
        assertThat(t.shape).isEqualTo(TotalTime.Shape.HOURS_MINUTES_SECONDS)
        assertThat(TotalTime.of(26 * 3_600_000L)).isEqualTo(TotalTime(26, 0, 0))
    }

    @Test
    fun `negative time never shows`() {
        assertThat(TotalTime.of(-5_000)).isEqualTo(TotalTime(0, 0, 0))
    }
}
