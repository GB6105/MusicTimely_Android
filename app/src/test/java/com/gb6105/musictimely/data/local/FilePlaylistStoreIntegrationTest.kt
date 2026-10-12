package com.gb6105.musictimely.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gb6105.musictimely.domain.model.playlist.Artwork
import com.gb6105.musictimely.domain.model.playlist.PlaylistEntry
import com.gb6105.musictimely.domain.model.playlist.SessionPlaylist
import com.gb6105.musictimely.domain.model.source.Provider
import com.gb6105.musictimely.testing.FakeClock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Base64

/** F18 M2 기록 보관 (DEC-A18): noBackup 원자 저장, 순서·반복 보존, 결과 닫기·전체 삭제·24시간 경과 시 삭제, 손상은 빈 목록. */
@RunWith(AndroidJUnit4::class)
class FilePlaylistStoreIntegrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = FakeClock(wallEpochMs = 1_760_000_000_000)
    private val store = FilePlaylistStore(context, clock)
    private val file = File(context.noBackupFilesDir, FilePlaylistStore.FILE_NAME)

    private fun entry(title: String, artist: String? = "June Archive", provider: Provider = Provider.MELON) =
        PlaylistEntry(0, title, artist, provider)

    @Test
    fun `appended songs read back in play order with repeats and numbering`() = runTest {
        store.append("s", entry("Soft Landing"))
        store.append("s", entry("Window Seat", artist = null, provider = Provider.SPOTIFY))
        store.append("s", entry("Soft Landing"))

        assertThat(store.playlist("s")).containsExactly(
            PlaylistEntry(1, "Soft Landing", "June Archive", Provider.MELON),
            PlaylistEntry(2, "Window Seat", null, Provider.SPOTIFY),
            PlaylistEntry(3, "Soft Landing", "June Archive", Provider.MELON),
        ).inOrder()
    }

    @Test
    fun `a playlist survives a new store instance (process restart) and lives in noBackupFilesDir`() = runTest {
        store.append("s", entry("Soft Landing"))

        assertThat(file.exists()).isTrue()
        assertThat(FilePlaylistStore(context, clock).playlist("s").map { it.title }).containsExactly("Soft Landing")
    }

    @Test
    fun `sessions are kept apart and deleting one leaves the other`() = runTest {
        store.append("a", entry("x"))
        store.append("b", entry("y"))

        store.delete("a")

        assertThat(FilePlaylistStore(context, clock).playlist("a")).isEmpty()
        assertThat(FilePlaylistStore(context, clock).playlist("b").map { it.title }).containsExactly("y")
    }

    @Test
    fun `clear removes every playlist and the file`() = runTest {
        store.append("a", entry("x"))

        store.clear()

        assertThat(file.exists()).isFalse()
        assertThat(FilePlaylistStore(context, clock).playlist("a")).isEmpty()
    }

    @Test
    fun `playlists last written more than 24 hours ago are purged`() = runTest {
        store.append("old", entry("x"))
        clock.wallEpochMs += SessionPlaylist.TTL_MS
        store.append("new", entry("y"))
        clock.wallEpochMs += 1

        store.purgeOlderThan(clock.wallNowEpochMs() - SessionPlaylist.TTL_MS)

        val reopened = FilePlaylistStore(context, clock)
        assertThat(reopened.playlist("old")).isEmpty()
        assertThat(reopened.playlist("new").map { it.title }).containsExactly("y")
    }

    @Test
    fun `a corrupt file reads as empty, never throws, and is replaced by the next append`() = runTest {
        file.parentFile?.mkdirs()
        file.writeText("{not json")

        assertThat(store.playlist("s")).isEmpty()
        store.purgeOlderThan(0)
        store.append("s", entry("x"))

        assertThat(FilePlaylistStore(context, clock).playlist("s").map { it.title }).containsExactly("x")
    }

    @Test
    fun `an entry with a wrong shape is not invented into a song`() = runTest {
        file.parentFile?.mkdirs()
        file.writeText("""{"schemaVersion":1,"sessions":[{"sessionId":"s","updatedAtEpochMs":1,"entries":[{"artist":"A"}]}]}""")

        assertThat(store.playlist("s")).isEmpty()
    }

    // ---- DEC-A21 앨범 아트: 곡명과 같은 파일·같은 수명 ----

    private val art = Artwork.of(ByteArray(300) { (it * 7).toByte() })!!
    private val artText = Base64.getEncoder().encodeToString(art.bytes)

    private fun rawEntries(artwork: String) =
        """{"schemaVersion":1,"sessions":[{"sessionId":"s","updatedAtEpochMs":1,"entries":[{"order":1,"title":"a","provider":"MELON","artwork":"$artwork"}]}]}"""

    @Test
    fun `artwork round trips with its song through a new store instance (DEC-A21)`() = runTest {
        store.append("s", entry("Soft Landing").copy(artwork = art))
        store.append("s", entry("Window Seat"))

        val reopened = FilePlaylistStore(context, clock).playlist("s")

        assertThat(reopened.map { it.title to it.artwork }).containsExactly("Soft Landing" to art, "Window Seat" to null).inOrder()
    }

    @Test
    fun `deleting a session removes its artwork from storage (DEC-A21)`() = runTest {
        store.append("a", entry("x").copy(artwork = art))
        store.append("b", entry("y"))

        store.delete("a")

        assertThat(file.readText()).doesNotContain(artText)
        assertThat(FilePlaylistStore(context, clock).playlist("b").map { it.artwork }).containsExactly(null)
    }

    @Test
    fun `the 24 hour purge removes artwork with its playlist (DEC-A21)`() = runTest {
        store.append("old", entry("x").copy(artwork = art))
        clock.wallEpochMs += SessionPlaylist.TTL_MS
        store.append("new", entry("y"))
        clock.wallEpochMs += 1

        store.purgeOlderThan(clock.wallNowEpochMs() - SessionPlaylist.TTL_MS)

        assertThat(file.readText()).doesNotContain(artText)
    }

    @Test
    fun `corrupt artwork reads as no artwork and keeps the song (DEC-A21)`() = runTest {
        file.parentFile?.mkdirs()
        file.writeText(rawEntries("@@not base64@@"))

        assertThat(store.playlist("s").map { it.title to it.artwork }).containsExactly("a" to null)
    }

    @Test
    fun `oversized artwork reads as no artwork and keeps the song (DEC-A21)`() = runTest {
        file.parentFile?.mkdirs()
        file.writeText(rawEntries(Base64.getEncoder().encodeToString(ByteArray(Artwork.MAX_BYTES + 1))))

        assertThat(store.playlist("s").map { it.title to it.artwork }).containsExactly("a" to null)
    }
}
