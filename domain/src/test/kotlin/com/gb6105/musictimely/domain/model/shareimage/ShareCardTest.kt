package com.gb6105.musictimely.domain.model.shareimage

import com.gb6105.musictimely.domain.model.playlist.Artwork
import com.gb6105.musictimely.domain.model.playlist.PlaylistEntry
import com.gb6105.musictimely.domain.model.result.TotalTime
import com.gb6105.musictimely.domain.model.source.Provider
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** F18 M5 공유 이미지 내용 (DEC-A22): 곡명·아티스트·총 걸린 시간만, 8곡 + "외 N곡", 앨범 아트 0 (DEC-A21). */
class ShareCardTest {

    private val time = TotalTime(0, 32, 18)
    private fun song(title: String, artist: String? = "June Archive", artwork: Artwork? = null) =
        PlaylistEntry(0, title, artist, Provider.MELON, artwork)

    @Test
    fun `an empty playlist has no share card (button stays disabled)`() {
        assertThat(ShareCard.of(time, emptyList())).isNull()
    }

    @Test
    fun `songs keep play order and repeats with title and artist`() {
        val card = ShareCard.of(time, listOf(song("a"), song("b", artist = null), song("a")))!!

        assertThat(card.songs).containsExactly(ShareSong("a", "June Archive"), ShareSong("b", null), ShareSong("a", "June Archive")).inOrder()
        assertThat(card.moreCount).isEqualTo(0)
        assertThat(card.totalTime).isEqualTo(time)
    }

    @Test
    fun `at most eight rows, the rest counted as more (DEC-A22)`() {
        val card = ShareCard.of(time, (1..11).map { song("t$it") })!!

        assertThat(card.songs.map { it.title }).containsExactlyElementsIn((1..8).map { "t$it" }).inOrder()
        assertThat(card.moreCount).isEqualTo(3)
        assertThat(card.songCount).isEqualTo(11)
    }

    @Test
    fun `exactly eight songs have no more line`() {
        assertThat(ShareCard.of(time, (1..8).map { song("t$it") })!!.moreCount).isEqualTo(0)
    }

    @Test
    fun `album art never reaches the share card (DEC-A21)`() {
        val withArt = ShareCard.of(time, listOf(song("a", artwork = Artwork.of(byteArrayOf(1, 2, 3)))))
        val withoutArt = ShareCard.of(time, listOf(song("a")))

        assertThat(withArt).isEqualTo(withoutArt)
    }
}
