package com.gb6105.musictimely.domain.model.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** SRS Android §9.1 MusicSettings 기본값, FR-A047 끝나면 멈추기 조건. */
class MusicSettingsTest {

    @Test
    fun `defaults are 240 second songs with counting and stop-at-end off`() {
        val s = MusicSettings()

        assertThat(s.durationPerUnitMs).isEqualTo(240_000)
        assertThat(s.songCountingEnabled).isFalse()
        assertThat(s.stopAtEndEnabled).isFalse()
        assertThat(s.permissionPromptShown).isFalse()
        assertThat(s.ytmNoticeShown).isFalse()
        assertThat(s.showSongs).isTrue()
        assertThat(s.selectedSourceId).isNull()
    }

    @Test
    fun `stop-at-end is usable only when song counting is on (FR-A047)`() {
        assertThat(MusicSettings(stopAtEndEnabled = true).stopAtEndUsable).isFalse()
        assertThat(MusicSettings(songCountingEnabled = true, stopAtEndEnabled = true).stopAtEndUsable).isTrue()
        assertThat(MusicSettings(songCountingEnabled = true).stopAtEndUsable).isFalse()
    }
}
