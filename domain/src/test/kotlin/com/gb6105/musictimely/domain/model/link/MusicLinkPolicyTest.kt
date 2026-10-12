package com.gb6105.musictimely.domain.model.link

import com.gb6105.musictimely.domain.model.source.Provider
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** F05 링크 규칙. FR-A023·A024·A029, SRS Android §7.5 (DEC-A09), QA-A02·A03, QA-20. */
class MusicLinkPolicyTest {

    @Test
    fun `extracts the first url from shared text`() {
        val text = "유튜브 뮤직에서 이 플레이리스트를 확인하세요: https://music.youtube.com/playlist?list=PL123&si=abc 그리고 더"

        assertThat(MusicLinkPolicy.extractFirstUrl(text)).isEqualTo("https://music.youtube.com/playlist?list=PL123&si=abc")
    }

    @Test
    fun `strips trailing punctuation around a shared url`() {
        assertThat(MusicLinkPolicy.extractFirstUrl("(https://open.spotify.com/playlist/37i9?si=x).")).isEqualTo("https://open.spotify.com/playlist/37i9?si=x")
    }

    @Test
    fun `text without a url has nothing to extract`() {
        assertThat(MusicLinkPolicy.extractFirstUrl("그냥 메모")).isNull()
    }

    @Test
    fun `youtube music keeps list and drops si and feature (QA-A02)`() {
        val result = MusicLinkPolicy.validate("https://music.youtube.com/playlist?list=PL123&si=abc&feature=share")

        assertThat(result).isEqualTo(LinkValidation.Accepted(Provider.YOUTUBE_MUSIC, "https://music.youtube.com/playlist?list=PL123"))
    }

    @Test
    fun `spotify drops si and utm parameters on both hosts`() {
        assertThat(MusicLinkPolicy.validate("https://open.spotify.com/playlist/37i9?si=x&utm_source=copy&nd=1"))
            .isEqualTo(LinkValidation.Accepted(Provider.SPOTIFY, "https://open.spotify.com/playlist/37i9?nd=1"))
        assertThat(MusicLinkPolicy.validate("https://spotify.link/AbC?utm_medium=share"))
            .isEqualTo(LinkValidation.Accepted(Provider.SPOTIFY, "https://spotify.link/AbC"))
    }

    @Test
    fun `apple music drops tracking parameters and keeps the item id`() {
        assertThat(MusicLinkPolicy.validate("https://music.apple.com/kr/album/x/123?i=456&at=1l3v&ct=share&ls=1&itscg=30200&itsct=music_box"))
            .isEqualTo(LinkValidation.Accepted(Provider.APPLE_MUSIC, "https://music.apple.com/kr/album/x/123?i=456"))
    }

    @Test
    fun `fragments are dropped and host matching ignores case`() {
        assertThat(MusicLinkPolicy.validate("https://MUSIC.youtube.com/watch?v=abc#t=10"))
            .isEqualTo(LinkValidation.Accepted(Provider.YOUTUBE_MUSIC, "https://music.youtube.com/watch?v=abc"))
    }

    @Test
    fun `non https schemes are rejected (FR-A029, QA-20)`() {
        listOf(
            "http://music.youtube.com/playlist?list=PL1",
            "javascript:alert(1)",
            "file:///sdcard/a.mp3",
            "intent://scan/#Intent;scheme=zxing;end",
            "melon://play?id=1",
            "content://media/external/audio/1",
        ).forEach {
            assertThat(MusicLinkPolicy.validate(it)).isEqualTo(LinkValidation.RejectedScheme)
        }
    }

    @Test
    fun `https links outside the allowlist are unsupported and offer app-only (FR-A024, QA-A03)`() {
        listOf(
            "https://www.youtube.com/watch?v=abc",
            "https://music.youtube.com.evil.example/playlist?list=1",
            "https://www.melon.com/playlist/1",
            "https://evil.example/?u=music.youtube.com",
        ).forEach {
            assertThat(MusicLinkPolicy.validate(it)).isEqualTo(LinkValidation.Unsupported)
        }
    }

    @Test
    fun `credentials or a custom port are rejected`() {
        assertThat(MusicLinkPolicy.validate("https://user:pw@music.youtube.com/playlist?list=1")).isEqualTo(LinkValidation.Unsupported)
        assertThat(MusicLinkPolicy.validate("https://music.youtube.com:8443/playlist?list=1")).isEqualTo(LinkValidation.Unsupported)
    }

    @Test
    fun `malformed text is rejected without throwing`() {
        assertThat(MusicLinkPolicy.validate("https://")).isEqualTo(LinkValidation.Unsupported)
        assertThat(MusicLinkPolicy.validate("not a link")).isEqualTo(LinkValidation.RejectedScheme)
    }

    @Test
    fun `sharing text goes through extraction then validation`() {
        assertThat(MusicLinkPolicy.fromSharedText("들어보세요 https://open.spotify.com/album/1?si=z"))
            .isEqualTo(LinkValidation.Accepted(Provider.SPOTIFY, "https://open.spotify.com/album/1"))
        assertThat(MusicLinkPolicy.fromSharedText("링크 없음")).isEqualTo(LinkValidation.NoLink)
    }
}
