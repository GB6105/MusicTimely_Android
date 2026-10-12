package com.gb6105.musictimely.ui.result

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gb6105.musictimely.domain.model.playlist.Artwork
import com.gb6105.musictimely.domain.model.playlist.PlaylistEntry
import com.gb6105.musictimely.domain.model.source.Provider
import com.gb6105.musictimely.domain.model.timer.Allocation
import com.gb6105.musictimely.domain.model.timer.EndReason
import com.gb6105.musictimely.domain.model.timer.TimerSession
import com.gb6105.musictimely.domain.model.timer.TimerState
import com.gb6105.musictimely.domain.repository.ObservedCount
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/** DEC-A21 결과 플레이리스트의 실제 앨범 아트 (앱 안에서만): 있으면 이미지, 없거나 읽지 못하면 기존 모티프. 장식(설명 없음). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ResultArtworkFeatureTest {

    @get:Rule val compose = createComposeRule()

    private val art: Artwork = ByteArrayOutputStream().let { out ->
        Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }.compress(Bitmap.CompressFormat.PNG, 100, out)
        Artwork.of(out.toByteArray())!!
    }

    private fun song(title: String, artwork: Artwork?) = PlaylistEntry(0, title, "June Archive", Provider.MELON, artwork)

    private fun show(vararg songs: PlaylistEntry) {
        val numbered = songs.mapIndexed { i, e -> e.copy(order = i + 1) }
        val session = TimerSession(
            sessionId = "s", state = TimerState.ENDED, allocation = Allocation(1_500_000, 1_500_000, 0),
            accumulatedActiveMs = 1_500_000, runningAnchorMonoMs = null, extensions = emptyList(),
            endReason = EndReason.COMPLETED, appliedCommandIds = emptySet(), taskTitle = "기획안",
        )
        compose.setContent {
            ResultScreen(ResultUiState.from(session, ObservedCount(songs.size, startedMidSession = false), numbered), onRestart = {})
        }
    }

    private fun exists(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `a row with artwork shows the image tile, a row without shows the motif tile`() {
        show(song("a", art), song("b", null))

        assertThat(exists(ResultTags.coverImage(0))).isTrue()
        assertThat(exists(ResultTags.cover(0))).isFalse()
        assertThat(exists(ResultTags.cover(1))).isTrue()
        assertThat(exists(ResultTags.coverImage(1))).isFalse()
    }

    @Test
    fun `the collage uses the first four songs' artwork and fills the rest with motifs`() {
        show(song("a", art), song("b", null), song("c", art), song("d", null), song("e", art))

        assertThat(exists(ResultTags.collageImage(0))).isTrue()
        assertThat(exists(ResultTags.collageImage(1))).isFalse()
        assertThat(exists(ResultTags.collageImage(2))).isTrue()
        assertThat(exists(ResultTags.collageImage(3))).isFalse()
        assertThat(compose.onAllNodesWithTag(ResultTags.COLLAGE_TILE).fetchSemanticsNodes()).hasSize(2)
    }

    @Test
    fun `unreadable artwork bytes fall back to the motif`() {
        show(song("a", Artwork.of(byteArrayOf(1, 2, 3))))

        assertThat(exists(ResultTags.coverImage(0))).isFalse()
        assertThat(exists(ResultTags.cover(0))).isTrue()
    }

    @Test
    fun `artwork tiles stay decorative without a content description`() {
        show(song("a", art))

        val image = compose.onNodeWithTag(ResultTags.coverImage(0), useUnmergedTree = true).fetchSemanticsNode()
        assertThat(image.config.contains(SemanticsProperties.ContentDescription)).isFalse()
    }
}
