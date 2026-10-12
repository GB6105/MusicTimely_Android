package com.gb6105.musictimely.domain.model.observation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** FR-A042 음악 멈춤 힌트 (10초, 세션당 1회), QA-A24. */
class MusicStoppedHintTest {

    @Test
    fun `shows once after the observed music stays paused for 10 seconds`() {
        var hint = MusicStoppedHint()
        hint = hint.onPlayback(isPlaying = false, nowMono = 1_000).next
        assertThat(hint.onPlayback(isPlaying = false, nowMono = 10_999).show).isFalse()

        val due = hint.onPlayback(isPlaying = false, nowMono = 11_000)
        assertThat(due.show).isTrue()

        val later = due.next.onPlayback(isPlaying = true, nowMono = 12_000).next
            .onPlayback(isPlaying = false, nowMono = 13_000).next
            .onPlayback(isPlaying = false, nowMono = 30_000)
        assertThat(later.show).isFalse()
    }

    @Test
    fun `playing again before 10 seconds resets the wait`() {
        val hint = MusicStoppedHint()
            .onPlayback(false, 0).next
            .onPlayback(true, 5_000).next
            .onPlayback(false, 6_000).next

        assertThat(hint.onPlayback(false, 12_000).show).isFalse()
        assertThat(hint.onPlayback(false, 16_000).show).isTrue()
    }
}
