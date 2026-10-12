package com.gb6105.musictimely.domain.model.translation

enum class CueKind {
    /** "이 곡이 끝나면 정한 시간이에요" — observed song boundary. */
    THIS_SONG_ENDS_AT_TARGET,

    /** "대략 한 곡 분량 남았어요" — time based. */
    ABOUT_ONE_SONG_LEFT,
}

/** The observed current item when its length and position are known. */
data class ObservedItem(val durationMs: Long, val positionMs: Long)

/**
 * Last-song cue (FR-A017, SRS Android §6.3), once per target revision (SRS v1 §6.5).
 * A revision whose starting remainder is already within one song is never cued (QA-17).
 */
data class LastSongCue(val revision: Int, val eligible: Boolean, val fired: Boolean) {
    data class Result(val fired: CueKind?, val next: LastSongCue)

    fun evaluate(remainingMs: Long, durationPerUnitMs: Long, observedItem: ObservedItem?): Result {
        if (!eligible || fired || remainingMs <= 0) return Result(null, this)
        val kind = if (observedItem != null) {
            CueKind.THIS_SONG_ENDS_AT_TARGET.takeIf { remainingMs <= observedItem.durationMs - observedItem.positionMs }
        } else {
            CueKind.ABOUT_ONE_SONG_LEFT.takeIf { remainingMs <= durationPerUnitMs }
        }
        return if (kind == null) Result(null, this) else Result(kind, copy(fired = true))
    }

    companion object {
        fun arm(revision: Int, remainingMs: Long, durationPerUnitMs: Long) =
            LastSongCue(revision = revision, eligible = remainingMs > durationPerUnitMs, fired = false)
    }
}
