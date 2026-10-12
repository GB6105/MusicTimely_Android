package com.gb6105.musictimely.domain.model.observation

/** Metadata of the observed session's current item. Never logged; counted items go to the playlist store only (DEC-A18). */
data class TrackMetadata(val packageName: String, val title: String?, val artist: String?, val durationMs: Long?)

/**
 * O2 song counting (SRS Android §6.2). Time is the session's active elapsed time, which only advances
 * while the timer is RUNNING, so transitions during PAUSED / LIMIT_REACHED are not counted (DEC-A14).
 *
 * Loop rule (DEC-A04 as interpreted here): a republished same-signature item is a loop only when its
 * length is known and it has been current for at least length − 5 s; earlier republishes (art loading)
 * change nothing (FR-A034). A position rewind from the last 5 s to the first 5 s is also a loop.
 */
data class SongCounter(
    val countedItems: Int,
    val loops: Int,
    val observedFromActiveMs: Long,
    val hadGap: Boolean,
    private val currentSignature: Long? = null,
    private val currentDurationMs: Long? = null,
    private val currentItemActiveMs: Long = 0,
    private val currentCounted: Boolean = false,
    private val lastActiveMs: Long = observedFromActiveMs,
    private val lastPositionMs: Long? = null,
    private val currentTrack: TrackMetadata? = null,
    /** The item this transition counted (countedItems went up by one), else null (F18 M1). */
    val justCounted: TrackMetadata? = null,
) {
    /** "지금 N번째 곡". */
    val currentIndex: Int get() = if (currentSignature != null && currentCounted) countedItems else countedItems + 1

    /** Counted items that already ended (chip fill). */
    val finishedCountedItems: Int get() = countedItems - if (currentSignature != null && currentCounted) 1 else 0

    /** Result label "(곡 세기 시작 후)" (DEC-A13). */
    val startedMidSession: Boolean get() = observedFromActiveMs > 0

    fun onTrack(track: TrackMetadata, atActiveMs: Long): SongCounter {
        val advanced = advanceTo(atActiveMs)
        // A transient empty title (e.g. while art loads) keeps the current item; 60 s without metadata degrades instead.
        val signature = signatureOf(track) ?: return advanced
        if (signature != advanced.currentSignature) {
            return advanced.copy(
                currentSignature = signature,
                currentTrack = track,
                currentDurationMs = track.durationMs,
                currentItemActiveMs = 0,
                currentCounted = false,
                lastPositionMs = null,
            )
        }
        val duration = track.durationMs ?: advanced.currentDurationMs
        val same = advanced.copy(currentDurationMs = duration)
        return if (duration != null && same.currentItemActiveMs >= duration - LOOP_EDGE_MS) same.loop() else same
    }

    fun onPosition(positionMs: Long, atActiveMs: Long): SongCounter {
        val advanced = advanceTo(atActiveMs)
        val duration = advanced.currentDurationMs
        val previous = advanced.lastPositionMs
        val rewound = duration != null && previous != null &&
            previous >= duration - LOOP_EDGE_MS && positionMs < LOOP_EDGE_MS
        return (if (rewound) advanced.loop() else advanced).copy(lastPositionMs = positionMs)
    }

    fun advanceTo(activeMs: Long): SongCounter {
        val delta = (activeMs - lastActiveMs).coerceAtLeast(0)
        val moved = copy(lastActiveMs = maxOf(lastActiveMs, activeMs), justCounted = null)
        if (currentSignature == null) return moved
        val itemMs = currentItemActiveMs + delta
        val tooShort = currentDurationMs != null && currentDurationMs < COUNT_THRESHOLD_MS
        val reaches = !currentCounted && !tooShort && itemMs >= COUNT_THRESHOLD_MS
        return moved.copy(
            currentItemActiveMs = itemMs,
            currentCounted = currentCounted || reaches,
            countedItems = countedItems + if (reaches) 1 else 0,
            justCounted = if (reaches) currentTrack else null,
        )
    }

    /** Observation lost: stop accumulating, never interpolate the gap. */
    fun onGap(atActiveMs: Long): SongCounter =
        advanceTo(atActiveMs).copy(hadGap = true, currentSignature = null, currentTrack = null, lastPositionMs = null)

    private fun loop() = copy(loops = loops + 1, currentItemActiveMs = 0, lastPositionMs = null)

    companion object {
        const val COUNT_THRESHOLD_MS = 30_000L
        private const val LOOP_EDGE_MS = 5_000L

        fun start(observedFromActiveMs: Long) = SongCounter(
            countedItems = 0,
            loops = 0,
            observedFromActiveMs = observedFromActiveMs,
            hadGap = false,
        )

        /** DEC-A03: hash(package, title, artist); duration excluded because some apps fill it later. */
        private fun signatureOf(track: TrackMetadata): Long? {
            val title = track.title?.takeIf { it.isNotBlank() } ?: return null
            return listOf(track.packageName, title, track.artist.orEmpty()).hashCode().toLong()
        }
    }
}
