package com.gb6105.musictimely.data.media

import android.graphics.Bitmap
import android.media.MediaMetadata
import android.os.Build
import com.gb6105.musictimely.domain.model.playlist.Artwork
import java.io.ByteArrayOutputStream

/**
 * Album art for the session playlist (DEC-A21): a bitmap the music app put in its metadata, center-cropped square,
 * longer edge at most [MAX_EDGE_PX], WebP lossy (JPEG below API 30). `*_URI` art is never read: no network here and
 * other apps' content URIs are not reliably readable. Any failure or a result above the limit is no artwork.
 */
object ArtworkEncoder {
    const val MAX_EDGE_PX = 120
    private const val QUALITY = 80
    private val BITMAP_KEYS = listOf(
        MediaMetadata.METADATA_KEY_ALBUM_ART,
        MediaMetadata.METADATA_KEY_ART,
        MediaMetadata.METADATA_KEY_DISPLAY_ICON,
    )

    fun fromMetadata(metadata: MediaMetadata, maxBytes: Int = Artwork.MAX_BYTES): ByteArray? = runCatching {
        BITMAP_KEYS.firstNotNullOfOrNull { metadata.getBitmap(it) }
    }.getOrNull()?.let { encode(it, maxBytes) }

    fun encode(source: Bitmap, maxBytes: Int = Artwork.MAX_BYTES): ByteArray? = runCatching {
        val side = minOf(source.width, source.height)
        val edge = minOf(side, MAX_EDGE_PX)
        val square = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
        val scaled = if (side == edge) square else Bitmap.createScaledBitmap(square, edge, edge, true)
        val out = ByteArrayOutputStream()
        val written = scaled.compress(format(), QUALITY, out)
        // The source belongs to the metadata; only the copies made here are released.
        if (scaled !== source) scaled.recycle()
        if (square !== source && square !== scaled) square.recycle()
        out.toByteArray().takeIf { written && it.isNotEmpty() && it.size <= maxBytes }
    }.getOrNull()

    private fun format(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.JPEG
}
