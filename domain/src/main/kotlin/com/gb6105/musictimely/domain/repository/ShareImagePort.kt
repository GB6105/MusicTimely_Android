package com.gb6105.musictimely.domain.repository

import com.gb6105.musictimely.domain.model.shareimage.ShareCard

/** Where a sent share image went (DEC-A22). */
enum class ShareTarget { INSTAGRAM, CHOOSER }

/**
 * The playlist share image (F18 M5–M7, DEC-A22): rendered on device into one temporary file, sent only on the
 * user's tap. Implementations never throw and never log song titles.
 */
interface ShareImagePort {
    /** Renders [card] and keeps it as the image to send, replacing the previous one. PNG bytes, or null on failure. */
    suspend fun prepare(card: ShareCard): ByteArray?

    /** Opens Instagram with the prepared image, else the system share sheet. Null when nothing could be opened. */
    fun send(): ShareTarget?

    /** Deletes the temporary image (result closed, data deleted, app start). */
    suspend fun clear()
}
