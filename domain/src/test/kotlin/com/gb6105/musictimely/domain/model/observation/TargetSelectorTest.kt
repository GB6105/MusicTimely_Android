package com.gb6105.musictimely.domain.model.observation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** DEC-A02 관측 대상 선택, FR-A033·A054. */
class TargetSelectorTest {

    private val own = "com.gb6105.musictimely"

    @Test
    fun `prefers the active session of the chosen source app`() {
        val sessions = listOf(MediaSessionInfo("com.spotify.music", isPlaying = true), MediaSessionInfo("ytm", isPlaying = false))

        assertThat(TargetSelector.select(preferredPackage = "ytm", sessions = sessions, ownPackage = own)?.packageName).isEqualTo("ytm")
    }

    @Test
    fun `otherwise takes the first playing session in list order`() {
        val sessions = listOf(
            MediaSessionInfo("podcast", isPlaying = false),
            MediaSessionInfo("com.iloen.melon", isPlaying = true),
            MediaSessionInfo("com.spotify.music", isPlaying = true),
        )

        assertThat(TargetSelector.select(preferredPackage = "ytm", sessions = sessions, ownPackage = own)?.packageName).isEqualTo("com.iloen.melon")
    }

    @Test
    fun `never targets this app and returns null when nothing qualifies`() {
        val sessions = listOf(MediaSessionInfo(own, isPlaying = true), MediaSessionInfo("podcast", isPlaying = false))

        assertThat(TargetSelector.select(preferredPackage = null, sessions = sessions, ownPackage = own)).isNull()
        assertThat(TargetSelector.select(preferredPackage = own, sessions = sessions, ownPackage = own)).isNull()
    }
}
