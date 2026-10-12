package com.gb6105.musictimely.domain.model.control

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 재생/일시정지 버튼 (DEC-A07). FR-A044, QA-A15. */
class TransportButtonTest {

    @Test
    fun `a tap sends the opposite of the current playback state`() {
        assertThat(TransportButton().onTap(isPlaying = true, nowMono = 0).command).isEqualTo(MusicCommand.PAUSE)
        assertThat(TransportButton().onTap(isPlaying = false, nowMono = 0).command).isEqualTo(MusicCommand.PLAY)
    }

    @Test
    fun `taps within 300 ms are debounced`() {
        val first = TransportButton().onTap(isPlaying = true, nowMono = 1_000)
        val confirmed = first.next.onPlayback(isPlaying = false)

        assertThat(confirmed.onTap(isPlaying = false, nowMono = 1_299).command).isNull()
        assertThat(confirmed.onTap(isPlaying = false, nowMono = 1_300).command).isEqualTo(MusicCommand.PLAY)
    }

    @Test
    fun `the expected state arriving in time keeps the app responsive`() {
        val sent = TransportButton().onTap(isPlaying = true, nowMono = 0).next
        val confirmed = sent.onPlayback(isPlaying = false)

        assertThat(confirmed.onTick(nowMono = 5_000).next.unresponsive).isFalse()
    }

    @Test
    fun `no expected state within 2 seconds marks the app unresponsive and notices once`() {
        val sent = TransportButton().onTap(isPlaying = true, nowMono = 0).next

        assertThat(sent.onTick(nowMono = 1_999).notice).isFalse()
        val timedOut = sent.onTick(nowMono = 2_000)
        assertThat(timedOut.notice).isTrue()
        assertThat(timedOut.next.unresponsive).isTrue()
        assertThat(timedOut.next.onTick(nowMono = 9_000).notice).isFalse()
    }

    @Test
    fun `once unresponsive the button opens the app instead of sending commands`() {
        val unresponsive = TransportButton().onTap(isPlaying = true, nowMono = 0).next.onTick(nowMono = 2_000).next

        val tap = unresponsive.onTap(isPlaying = true, nowMono = 5_000)

        assertThat(tap.command).isNull()
        assertThat(tap.openApp).isTrue()
    }

    @Test
    fun `an unresponsive app that later reports playback gets the button back (emulator finding)`() {
        val unresponsive = TransportButton().onTap(isPlaying = true, nowMono = 0).next.onTick(nowMono = 2_000).next

        val alive = unresponsive.onPlayback(isPlaying = true)
        val tap = alive.onTap(isPlaying = true, nowMono = 5_000)

        assertThat(alive.unresponsive).isFalse()
        assertThat(tap.command).isEqualTo(MusicCommand.PAUSE)
    }

    @Test
    fun `the unresponsive notice is shown once per session even after recovering (DEC-A07)`() {
        val first = TransportButton().onTap(isPlaying = true, nowMono = 0).next.onTick(nowMono = 2_000)
        assertThat(first.notice).isTrue()

        val second = first.next.onPlayback(isPlaying = true)
            .onTap(isPlaying = true, nowMono = 5_000).next
            .onTick(nowMono = 7_000)

        assertThat(second.next.unresponsive).isTrue()
        assertThat(second.notice).isFalse()
    }
}
