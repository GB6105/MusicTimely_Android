package com.gb6105.musictimely.domain.model.observation

/** FR-A042: observed music paused for 10 s → one non-blocking hint per session. The timer is unaffected. */
data class MusicStoppedHint(val pausedSinceMono: Long? = null, val shown: Boolean = false) {
    data class Result(val show: Boolean, val next: MusicStoppedHint)

    fun onPlayback(isPlaying: Boolean, nowMono: Long): Result {
        if (shown) return Result(false, this)
        if (isPlaying) return Result(false, copy(pausedSinceMono = null))
        val since = pausedSinceMono ?: nowMono
        return if (nowMono - since >= WAIT_MS) Result(true, copy(pausedSinceMono = since, shown = true))
        else Result(false, copy(pausedSinceMono = since))
    }

    private companion object {
        const val WAIT_MS = 10_000L
    }
}
