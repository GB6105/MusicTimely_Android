package com.gb6105.musictimely.domain.usecase.source

import com.gb6105.musictimely.domain.model.source.ExternalSource
import com.gb6105.musictimely.domain.model.source.Provider
import com.gb6105.musictimely.testing.FakeClock
import com.gb6105.musictimely.testing.FakeSourceRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** F05 외부 음악 등록 (MT-M14). FR-A023·A024·A025, QA-A02·A03. */
class RegisterSourceUseCaseTest {

    private val repository = FakeSourceRepository()
    private val clock = FakeClock(wallEpochMs = 1_000)
    private var nextId = 0
    private val register = RegisterSourceUseCase(repository, clock) { "src-${nextId++}" }

    @Test
    fun `a supported shared link becomes a pending link source with the normalized url`() {
        val result = register.inspectSharedText("이 플레이리스트 들어봐 https://music.youtube.com/playlist?list=PL1&si=x")

        assertThat(result).isEqualTo(SourceCandidate.Link(Provider.YOUTUBE_MUSIC, "https://music.youtube.com/playlist?list=PL1"))
    }

    @Test
    fun `an unsupported https link offers app-only, a bad scheme is rejected, no url is nothing`() {
        assertThat(register.inspectLink("https://www.melon.com/playlist/1")).isEqualTo(SourceCandidate.OfferAppOnly)
        assertThat(register.inspectLink("javascript:alert(1)")).isEqualTo(SourceCandidate.Rejected)
        assertThat(register.inspectSharedText("링크 없음")).isEqualTo(SourceCandidate.Rejected)
    }

    @Test
    fun `saving a link candidate stores it with the given label`() = runTest {
        val saved = register.saveLink(SourceCandidate.Link(Provider.SPOTIFY, "https://open.spotify.com/album/1"), label = "  주말 앨범 ")

        assertThat(saved).isEqualTo(
            ExternalSource("src-0", Provider.SPOTIFY, "com.spotify.music", "https://open.spotify.com/album/1", "주말 앨범", 1_000),
        )
        assertThat(repository.sources.value).containsExactly(saved)
    }

    @Test
    fun `a blank label falls back to the default label`() = runTest {
        val saved = register.saveLink(SourceCandidate.Link(Provider.SPOTIFY, "https://open.spotify.com/album/1"), label = " ", defaultLabel = "스포티파이 플레이리스트")

        assertThat(saved.label).isEqualTo("스포티파이 플레이리스트")
    }

    @Test
    fun `app-only stores a source without a link (FR-A025)`() = runTest {
        val saved = register.saveAppOnly(Provider.MELON, label = "멜론")

        assertThat(saved.linkUri).isNull()
        assertThat(saved.packageName).isEqualTo("com.iloen.melon")
    }

    @Test
    fun `OTHER has no package and cannot be saved as app-only`() = runTest {
        val error = runCatching { register.saveAppOnly(Provider.OTHER, label = "기타") }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
    }
}
