package com.gb6105.musictimely.domain.model.translation

/** Display alignment only (SRS v1 §6.2): elapsed, target and cue times stay unchanged. */
data class CorrectionAnchor(val anchorIndex: Int, val anchorElapsedMs: Long) {
    fun displayIndex(elapsedMs: Long, durationPerUnitMs: Long): Int =
        anchorIndex + ((elapsedMs - anchorElapsedMs).coerceAtLeast(0) / durationPerUnitMs).toInt()
}

object SongCorrection {
    private val RANGE = 1..999

    sealed interface Result {
        data class Applied(val anchor: CorrectionAnchor) : Result
        data object OutOfRange : Result
    }

    fun anchor(index: Int, elapsedMs: Long): Result =
        if (index in RANGE) Result.Applied(CorrectionAnchor(index, elapsedMs)) else Result.OutOfRange
}
