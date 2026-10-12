package com.gb6105.musictimely.domain.model.translation

/**
 * D0 time-to-song translation (SRS v1 §6.1). Display only: the timer target is never rounded to N × d.
 * T = allocation, G = current target, E = active elapsed, d = average song length snapshot.
 */
object SongTranslation {
    /** N = max(1, ceil(T / d)). */
    fun allocatedSongs(allocationMs: Long, durationPerUnitMs: Long): Int =
        maxOf(1, ceilDiv(allocationMs, durationPerUnitMs))

    /** R = max(0, ceil((G - E) / d)). */
    fun remainingSongs(targetMs: Long, elapsedMs: Long, durationPerUnitMs: Long): Int =
        maxOf(0, ceilDiv(targetMs - elapsedMs, durationPerUnitMs))

    /** U = E / d in tenths, rounded half up ("약 2.4곡 분량" → 24). */
    fun elapsedTenths(elapsedMs: Long, durationPerUnitMs: Long): Int =
        ((20 * elapsedMs + durationPerUnitMs) / (2 * durationPerUnitMs)).toInt()

    /** I = floor(E / d) + 1. */
    fun segmentIndex(elapsedMs: Long, durationPerUnitMs: Long): Int = (elapsedMs / durationPerUnitMs).toInt() + 1

    /** floor(E / d), except that the fractional last chip completes once E reaches G. */
    fun filledChips(targetMs: Long?, elapsedMs: Long, durationPerUnitMs: Long): Int {
        if (targetMs != null && elapsedMs >= targetMs) return allocatedSongs(targetMs, durationPerUnitMs)
        return (elapsedMs / durationPerUnitMs).toInt()
    }

    private fun ceilDiv(a: Long, b: Long): Int = if (a <= 0) 0 else ((a + b - 1) / b).toInt()
}
