package com.gb6105.musictimely.domain.model.playlist

/**
 * Album art the music app published for a counted song, already downscaled and encoded by the data layer
 * (DEC-A21). Same rules as titles (DEC-A18): on device with its session playlist only, never logged, sent or shared
 * (no share image, plan M5). Compares by content; its text form never carries the image.
 */
class Artwork private constructor(private val data: ByteArray) {
    /** A copy: the stored image cannot be changed from outside. */
    val bytes: ByteArray get() = data.copyOf()

    override fun equals(other: Any?): Boolean = other is Artwork && data.contentEquals(other.data)

    override fun hashCode(): Int = data.contentHashCode()

    override fun toString(): String = "Artwork(${data.size} bytes)"

    companion object {
        /** Encoded images above this are dropped, never stored (DEC-A21). */
        const val MAX_BYTES: Int = 24 * 1024

        fun of(bytes: ByteArray?): Artwork? =
            bytes?.takeIf { it.isNotEmpty() && it.size <= MAX_BYTES }?.let { Artwork(it.copyOf()) }
    }
}
