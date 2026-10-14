package com.gb6105.musictimely.domain.model.shareimage

import com.gb6105.musictimely.domain.model.playlist.PlaylistEntry
import com.gb6105.musictimely.domain.model.result.TotalTime

/** One song row of the share image: title and artist only. */
data class ShareSong(val title: String, val artist: String?)

/**
 * What the share image shows (F18 M5, DEC-A22): the total time and the counted songs in play order with repeats,
 * at most [MAX_ROWS] rows then "외 N곡". No task title, date or estimated count; no album art (DEC-A21), so a
 * card cannot carry it. Titles leave the device only when the user sends this card (DEC-A18).
 */
data class ShareCard(val totalTime: TotalTime, val songs: List<ShareSong>, val moreCount: Int) {
    val songCount: Int get() = songs.size + moreCount

    companion object {
        const val MAX_ROWS: Int = 8

        /** Null without confirmed songs: nothing to share (Figma A empty). */
        fun of(totalTime: TotalTime, playlist: List<PlaylistEntry>): ShareCard? {
            if (playlist.isEmpty()) return null
            return ShareCard(
                totalTime = totalTime,
                songs = playlist.take(MAX_ROWS).map { ShareSong(it.title, it.artist) },
                moreCount = (playlist.size - MAX_ROWS).coerceAtLeast(0),
            )
        }
    }
}
