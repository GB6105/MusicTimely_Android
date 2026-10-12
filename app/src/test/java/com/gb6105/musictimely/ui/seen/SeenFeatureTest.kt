package com.gb6105.musictimely.ui.seen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gb6105.musictimely.domain.model.observation.ObservationSnapshot
import com.gb6105.musictimely.domain.model.observation.ObservationState
import com.gb6105.musictimely.domain.model.observation.SeenEntry
import com.gb6105.musictimely.domain.model.observation.SeenKind
import com.gb6105.musictimely.domain.model.source.Provider
import com.gb6105.musictimely.testing.FakeMusicControl
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** SCR-13 이 앱이 본 것 (FR-A040, MT-A13, QA-A27). */
@RunWith(AndroidJUnit4::class)
class SeenFeatureTest {

    @get:Rule val compose = createComposeRule()

    private val music = FakeMusicControl()

    @Test
    fun `lists the connected app, current song, counted songs and sent commands of this session`() {
        music.snapshot.value = ObservationSnapshot(
            state = ObservationState.OBSERVING, provider = Provider.YOUTUBE_MUSIC, title = "Slow Tide", isPlaying = true, countedItems = 3,
            seen = listOf(
                SeenEntry(0, SeenKind.CONNECTED, Provider.YOUTUBE_MUSIC, null, null),
                SeenEntry(1_000, SeenKind.TRACK, Provider.YOUTUBE_MUSIC, "Slow Tide", "LUMEN FIELD"),
                SeenEntry(65_000, SeenKind.COMMAND_PAUSE, Provider.YOUTUBE_MUSIC, null, null),
            ),
        )

        compose.setContent { SeenRoute(SeenViewModel(music), onBack = {}) }

        compose.onNodeWithText("연결된 앱: 유튜브뮤직").assertIsDisplayed()
        compose.onNodeWithText("현재 곡: Slow Tide · LUMEN FIELD").assertIsDisplayed()
        compose.onNodeWithText("센 곡: 3곡").assertIsDisplayed()
        compose.onNodeWithText("보낸 명령: 일시정지 (+01:05)").assertIsDisplayed()
    }

    @Test
    fun `nothing observed says so (after the session the log is empty)`() {
        compose.setContent { SeenRoute(SeenViewModel(music), onBack = {}) }

        compose.onNodeWithText("지금은 아무것도 보고 있지 않아요").assertIsDisplayed()
    }
}
