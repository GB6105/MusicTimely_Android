package com.gb6105.musictimely.domain.model.control

/** The only two commands the app may send to another app's media session (ADR-0013, FR-A046). */
enum class MusicCommand { PLAY, PAUSE }

/**
 * User play/pause button (FR-A044, DEC-A07): one tap = one command, 300 ms debounce, and an app that does not
 * reflect the expected state within 2 s is marked unresponsive (notice once per session). Any later playback report
 * from the app shows it is alive again and gives the button back (emulator finding: one slow reply locked it).
 */
data class TransportButton(
    val unresponsive: Boolean = false,
    private val lastTapMono: Long? = null,
    private val expectPlaying: Boolean? = null,
    private val sentAtMono: Long? = null,
    private val noticed: Boolean = false,
) {
    data class TapResult(val command: MusicCommand?, val openApp: Boolean, val next: TransportButton)
    data class TickResult(val notice: Boolean, val next: TransportButton)

    fun onTap(isPlaying: Boolean, nowMono: Long): TapResult {
        if (unresponsive) return TapResult(command = null, openApp = true, next = this)
        if (lastTapMono != null && nowMono - lastTapMono < DEBOUNCE_MS) return TapResult(null, false, this)
        val command = if (isPlaying) MusicCommand.PAUSE else MusicCommand.PLAY
        return TapResult(command, false, copy(lastTapMono = nowMono, expectPlaying = !isPlaying, sentAtMono = nowMono))
    }

    fun onPlayback(isPlaying: Boolean): TransportButton = when {
        unresponsive -> copy(unresponsive = false)
        expectPlaying == isPlaying -> copy(expectPlaying = null, sentAtMono = null)
        else -> this
    }

    fun onTick(nowMono: Long): TickResult {
        val sent = sentAtMono ?: return TickResult(false, this)
        if (unresponsive || nowMono - sent < CONFIRM_MS) return TickResult(false, this)
        return TickResult(notice = !noticed, copy(unresponsive = true, expectPlaying = null, sentAtMono = null, noticed = true))
    }

    private companion object {
        const val DEBOUNCE_MS = 300L
        const val CONFIRM_MS = 2_000L
    }
}
