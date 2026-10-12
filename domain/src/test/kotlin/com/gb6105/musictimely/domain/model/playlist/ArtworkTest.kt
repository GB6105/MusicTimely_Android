package com.gb6105.musictimely.domain.model.playlist

import com.gb6105.musictimely.domain.model.observation.TrackMetadata
import com.gb6105.musictimely.domain.model.source.Provider
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** DEC-A21 앨범 아트: 곡명과 같은 보관 규칙(DEC-A18), 내용 비교, 24KB 상한, 내용을 문자열로 내보내지 않음. */
class ArtworkTest {

    @Test
    fun `artworks with the same bytes are equal and entries carrying them compare by content`() {
        val a = PlaylistEntry(1, "a", "A", Provider.MELON, Artwork.of(byteArrayOf(1, 2, 3)))
        val b = PlaylistEntry(1, "a", "A", Provider.MELON, Artwork.of(byteArrayOf(1, 2, 3)))

        assertThat(a).isEqualTo(b)
        assertThat(a.hashCode()).isEqualTo(b.hashCode())
        assertThat(a).isNotEqualTo(b.copy(artwork = Artwork.of(byteArrayOf(9))))
        assertThat(a).isNotEqualTo(b.copy(artwork = null))
    }

    @Test
    fun `empty or oversized bytes are no artwork`() {
        assertThat(Artwork.of(null)).isNull()
        assertThat(Artwork.of(ByteArray(0))).isNull()
        assertThat(Artwork.of(ByteArray(Artwork.MAX_BYTES))).isNotNull()
        assertThat(Artwork.of(ByteArray(Artwork.MAX_BYTES + 1))).isNull()
    }

    @Test
    fun `the stored bytes cannot be changed from outside`() {
        val source = byteArrayOf(1, 2, 3)
        val art = Artwork.of(source)!!

        source[0] = 9
        art.bytes[1] = 9

        assertThat(art.bytes.toList()).containsExactly(1.toByte(), 2.toByte(), 3.toByte()).inOrder()
    }

    @Test
    fun `text form never carries the image content`() {
        assertThat(Artwork.of(byteArrayOf(65, 66, 67))!!.toString()).isEqualTo("Artwork(3 bytes)")
    }

    @Test
    fun `a counted entry carries the artwork captured for that track`() {
        val art = Artwork.of(byteArrayOf(7))
        val entry = PlaylistEntry.of(2, TrackMetadata("com.iloen.melon", "a", "A", null), art)

        assertThat(entry.artwork).isEqualTo(art)
        assertThat(PlaylistEntry.of(2, TrackMetadata("com.iloen.melon", "a", "A", null)).artwork).isNull()
    }
}
