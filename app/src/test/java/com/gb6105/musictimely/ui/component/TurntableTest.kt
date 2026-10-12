package com.gb6105.musictimely.ui.component

import com.gb6105.musictimely.domain.model.audio.NoiseKind
import com.gb6105.musictimely.ui.session.MusicButton
import com.gb6105.musictimely.ui.session.MusicPanelState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** DEC-A12 레코드·톤암: 관측된 재생 중에만 톤암 내림 + 회전. 그 외 들림 + 정지. 모션 줄이기면 애니메이션 없음. */
class TurntableTest {

    @Test
    fun `only observed playing music lowers the arm and spins the record`() {
        val playing = MusicPanelState.Observed("a", playing = true, handoffProvider = null, button = MusicButton.PAUSE)
        assertThat(TurntableState.of(playing)).isEqualTo(TurntableState(armDown = true, spinning = true))
    }

    @Test
    fun `paused, unobserved, estimated, noise and no music keep the arm up and the record still`() {
        listOf(
            MusicPanelState.Observed("a", playing = false, handoffProvider = null, button = MusicButton.PLAY),
            MusicPanelState.ExternalOff("유튜브뮤직"),
            MusicPanelState.ExternalWaiting("유튜브뮤직"),
            MusicPanelState.Empty,
            MusicPanelState.None,
            MusicPanelState.Noise(NoiseKind.PINK, playing = true),
        ).forEach { panel ->
            assertThat(TurntableState.of(panel)).isEqualTo(TurntableState(armDown = false, spinning = false))
        }
    }

    @Test
    fun `arm angles and timings follow the design`() {
        assertThat(TurntableState.ARM_DOWN_DEGREES).isEqualTo(30f)
        assertThat(TurntableState.ARM_UP_DEGREES).isEqualTo(0f)
        assertThat(TurntableState.LOWER_MS).isEqualTo(650)
        assertThat(TurntableState.LIFT_MS).isEqualTo(550)
    }
}
