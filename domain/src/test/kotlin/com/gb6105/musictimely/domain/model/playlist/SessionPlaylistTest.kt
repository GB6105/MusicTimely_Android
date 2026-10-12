package com.gb6105.musictimely.domain.model.playlist

import com.gb6105.musictimely.domain.model.session.RestorePolicy
import com.gb6105.musictimely.domain.model.source.Provider
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** F18 M2 보관 규칙 (DEC-A18): 재생 순서·반복 보존, 마지막 기록 후 24시간 지나면 만료. */
class SessionPlaylistTest {

    private fun entry(title: String) = PlaylistEntry(0, title, "A", Provider.MELON)

    @Test
    fun `appending numbers entries in arrival order and keeps repeats`() {
        val list = SessionPlaylist("s", emptyList(), 0)
            .append(entry("a"), 10)
            .append(entry("b"), 20)
            .append(entry("a"), 30)

        assertThat(list.entries.map { it.title to it.order }).containsExactly("a" to 1, "b" to 2, "a" to 3).inOrder()
        assertThat(list.updatedAtEpochMs).isEqualTo(30)
    }

    @Test
    fun `a playlist expires 24 hours after its last record, like an unconfirmed result`() {
        val list = SessionPlaylist("s", listOf(entry("a")), updatedAtEpochMs = 1_000)

        assertThat(SessionPlaylist.TTL_MS).isEqualTo(RestorePolicy.RESULT_TTL_MS)
        assertThat(list.isOlderThan(1_000)).isFalse()
        assertThat(list.isOlderThan(1_001)).isTrue()
    }
}
