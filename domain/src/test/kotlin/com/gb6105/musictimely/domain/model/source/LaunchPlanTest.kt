package com.gb6105.musictimely.domain.model.source

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** FR-A026 열기 폴백 순서 (SRS Android §7.4), FR-A025 지원 패키지 (§7.1). */
class LaunchPlanTest {

    private val link = ExternalSource(
        id = "a", provider = Provider.YOUTUBE_MUSIC, packageName = "com.google.android.apps.youtube.music",
        linkUri = "https://music.youtube.com/playlist?list=PL1", label = "유튜브뮤직 플레이리스트", createdAtEpochMs = 0,
    )

    @Test
    fun `a link source tries the link in the app, then the app, then the store, then manual`() {
        assertThat(LaunchPlan.of(link)).containsExactly(
            LaunchStep.ViewLinkInPackage("https://music.youtube.com/playlist?list=PL1", "com.google.android.apps.youtube.music"),
            LaunchStep.LaunchApp("com.google.android.apps.youtube.music"),
            LaunchStep.OpenStoreListing("com.google.android.apps.youtube.music"),
            LaunchStep.ShowManualGuide,
        ).inOrder()
    }

    @Test
    fun `an app-only source launches the app, then the store, then manual`() {
        assertThat(LaunchPlan.of(link.copy(linkUri = null))).containsExactly(
            LaunchStep.LaunchApp("com.google.android.apps.youtube.music"),
            LaunchStep.OpenStoreListing("com.google.android.apps.youtube.music"),
            LaunchStep.ShowManualGuide,
        ).inOrder()
    }

    @Test
    fun `supported providers map to the declared query packages`() {
        assertThat(Provider.YOUTUBE_MUSIC.packageName).isEqualTo("com.google.android.apps.youtube.music")
        assertThat(Provider.SPOTIFY.packageName).isEqualTo("com.spotify.music")
        assertThat(Provider.MELON.packageName).isEqualTo("com.iloen.melon")
        assertThat(Provider.GENIE.packageName).isEqualTo("com.ktmusic.geniemusic")
        assertThat(Provider.FLO.packageName).isEqualTo("skplanet.musicmate")
        assertThat(Provider.APPLE_MUSIC.packageName).isEqualTo("com.apple.android.music")
        assertThat(Provider.OTHER.packageName).isNull()
    }

    @Test
    fun `provider lookup by package name`() {
        assertThat(Provider.fromPackage("com.iloen.melon")).isEqualTo(Provider.MELON)
        assertThat(Provider.fromPackage("com.example.player")).isEqualTo(Provider.OTHER)
    }
}
