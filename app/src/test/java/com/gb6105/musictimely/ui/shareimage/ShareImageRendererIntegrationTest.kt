package com.gb6105.musictimely.ui.shareimage

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gb6105.musictimely.domain.model.result.TotalTime
import com.gb6105.musictimely.domain.model.shareimage.ShareCard
import com.gb6105.musictimely.domain.model.shareimage.ShareSong
import com.gb6105.musictimely.ui.shareimage.ShareImageRenderer.Companion.COVER
import com.gb6105.musictimely.ui.shareimage.ShareImageRenderer.Companion.PADDING
import com.gb6105.musictimely.ui.shareimage.ShareImageRenderer.Companion.coverTop
import com.gb6105.musictimely.ui.theme.MtColors
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** F18 M5 공유 이미지 (DEC-A22): 1080×1920 라이트, 앱 표기·제목·총 걸린 시간·곡 8줄 + "외 N곡", 곡마다 앱 모티프(앨범 아트 0, DEC-A21). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShareImageRendererIntegrationTest {

    private val renderer = ShareImageRenderer(ApplicationProvider.getApplicationContext<Application>())
    private val background = MtColors.Light.background.toArgb()
    private fun card(count: Int, title: (Int) -> String = { "Song $it" }, artist: String? = "June Archive") =
        ShareCard(TotalTime(0, 32, 18), (1..minOf(count, ShareCard.MAX_ROWS)).map { ShareSong(title(it), artist) }, (count - ShareCard.MAX_ROWS).coerceAtLeast(0))

    private fun Bitmap.coverPixel(row: Int, x: Int = COVER / 2, y: Int = 4) = getPixel(PADDING + x, coverTop(row) + y)

    @Test
    fun `the image is a 1080 by 1920 light canvas (DEC-A22)`() {
        val image = renderer.render(card(3))

        assertThat(image.width).isEqualTo(1080)
        assertThat(image.height).isEqualTo(1920)
        assertThat(image.getPixel(2, 2)).isEqualTo(background)
        assertThat(image.getPixel(1077, 1917)).isEqualTo(background)
    }

    @Test
    fun `it shows the mark, the title and the total time, never a task title, date or estimate`() {
        val texts = renderer.texts(card(3))

        assertThat(texts.mark).isEqualTo("MUSIC TIMELY")
        assertThat(texts.title).isEqualTo("이번 플레이리스트")
        assertThat(texts.totalTime).isEqualTo("총 걸린 시간 32분 18초")
        assertThat(texts.all().joinToString("|")).doesNotContainMatch("분량|약 |\\d{4}[.-]|기획안")
    }

    @Test
    fun `songs are listed in order with title and artist`() {
        val texts = renderer.texts(card(3))

        assertThat(texts.rows).containsExactly(
            ShareImageRenderer.Row("Song 1", "June Archive"),
            ShareImageRenderer.Row("Song 2", "June Archive"),
            ShareImageRenderer.Row("Song 3", "June Archive"),
        ).inOrder()
        assertThat(texts.more).isNull()
    }

    @Test
    fun `at most eight rows are drawn, then 외 N곡 (DEC-A22)`() {
        val image = renderer.render(card(11))
        val texts = renderer.texts(card(11))

        assertThat(texts.rows).hasSize(8)
        assertThat(texts.more).isEqualTo("외 3곡")
        (0 until 8).forEach { row -> assertWithMessage("cover $row").that(image.coverPixel(row)).isNotEqualTo(background) }
        assertWithMessage("no ninth cover").that(image.coverPixel(8, y = COVER / 2)).isEqualTo(background)
    }

    @Test
    fun `each row shows the app cover motif, cycling sleeve, stripes, record (no album art, DEC-A21)`() {
        val image = renderer.render(card(3))

        assertThat(image.coverPixel(0)).isEqualTo(0xFFDADADD.toInt()) // sleeve tile grey
        assertThat(image.coverPixel(1)).isEqualTo(MtColors.Charcoal.toArgb()) // stripes
        assertThat(image.coverPixel(2)).isEqualTo(0xFFDADADD.toInt()) // record tile grey
    }

    @Test
    fun `a long title is cut to one line with an ellipsis`() {
        val long = "아주 긴 곡 제목이 한 줄을 훨씬 넘어서 계속 이어지는 경우에도 이미지 밖으로 나가지 않아요 Extended Mix"
        val row = renderer.texts(card(1, title = { long })).rows.single()

        assertThat(row.title).endsWith("…")
        assertThat(row.title.length).isLessThan(long.length)
        assertThat(row.title).doesNotContain("\n")
    }

    @Test
    fun `without an artist the row has the title only`() {
        assertThat(renderer.texts(card(1, artist = null)).rows.single()).isEqualTo(ShareImageRenderer.Row("Song 1", null))
    }

    @Test
    fun `exactly eight songs have no more line`() {
        assertThat(renderer.texts(card(8)).more).isNull()
    }
}
