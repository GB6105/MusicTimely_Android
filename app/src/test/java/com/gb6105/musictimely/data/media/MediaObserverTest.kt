package com.gb6105.musictimely.data.media

import com.gb6105.musictimely.domain.model.control.MusicCommand
import com.gb6105.musictimely.domain.model.observation.ObservationState
import com.gb6105.musictimely.domain.model.observation.SeenKind
import com.gb6105.musictimely.domain.model.observation.TrackMetadata
import com.gb6105.musictimely.domain.model.playlist.Artwork
import com.gb6105.musictimely.domain.model.playlist.PlaylistEntry
import com.gb6105.musictimely.domain.model.source.Provider
import com.gb6105.musictimely.testing.FakeClock
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * F06 관측 조율 (SRS Android §5.2·§7.2). FR-A030·A033·A036·A037·A039·A040·A041·A054,
 * QA-A06·A11·A12·A23·A27·A28.
 */
class MediaObserverTest {

    private val ytm = "com.google.android.apps.youtube.music"
    private val melon = "com.iloen.melon"
    private val own = "com.gb6105.musictimely"
    private val gateway = FakeMediaSessionGateway()
    private val clock = FakeClock()
    private var activeMs = 0L
    private var countingOn = true
    private val observer = MediaObserver(gateway, clock, ownPackage = own, countingEnabled = { countingOn }) { activeMs }

    private fun song(title: String, pkg: String = ytm) = TrackMetadata(pkg, title, "A", 200_000)

    @Test
    fun `stays OFF when the in-app switch is off and never queries sessions (FR-A041)`() {
        countingOn = false
        gateway.revoked = true

        observer.start(preferredPackage = ytm)

        assertThat(observer.snapshot.value.state).isEqualTo(ObservationState.OFF)
    }

    @Test
    fun `stays OFF without notification access (QA-A01)`() {
        gateway.granted = false

        observer.start(preferredPackage = ytm)

        assertThat(observer.snapshot.value.state).isEqualTo(ObservationState.OFF)
    }

    @Test
    fun `observes the chosen app's playing session and shows its title (QA-A06)`() {
        gateway.add(FakeSession(ytm, track = song("Slow Tide")))

        observer.start(preferredPackage = ytm)

        val s = observer.snapshot.value
        assertThat(s.state).isEqualTo(ObservationState.OBSERVING)
        assertThat(s.provider).isEqualTo(Provider.YOUTUBE_MUSIC)
        assertThat(s.title).isEqualTo("Slow Tide")
        assertThat(s.isPlaying).isTrue()
        assertThat(s.startedMidSession).isFalse()
    }

    @Test
    fun `searches until a session appears`() {
        observer.start(preferredPackage = ytm)
        assertThat(observer.snapshot.value.state).isEqualTo(ObservationState.SEARCHING)

        gateway.add(FakeSession(ytm, track = song("a")))

        assertThat(observer.snapshot.value.state).isEqualTo(ObservationState.OBSERVING)
    }

    @Test
    fun `counts songs by running time (QA-A07)`() {
        val session = gateway.add(FakeSession(ytm, track = song("a")))
        observer.start(preferredPackage = ytm)

        activeMs = 200_000
        session.changeTrack("b")
        activeMs = 400_000
        session.changeTrack("c")
        activeMs = 430_000
        observer.onActiveTime()

        assertThat(observer.snapshot.value.countedItems).isEqualTo(3)
        assertThat(observer.snapshot.value.title).isEqualTo("c")
    }

    @Test
    fun `hands off to another playing app and keeps the count (QA-A11)`() {
        val first = gateway.add(FakeSession(ytm, track = song("a")))
        observer.start(preferredPackage = ytm)
        activeMs = 100_000
        observer.onActiveTime()

        gateway.remove(first)
        gateway.add(FakeSession(melon, track = song("b", melon)))

        val s = observer.snapshot.value
        assertThat(s.state).isEqualTo(ObservationState.OBSERVING)
        assertThat(s.provider).isEqualTo(Provider.MELON)
        assertThat(s.handedOff).isTrue()
        assertThat(s.countedItems).isEqualTo(1)
    }

    @Test
    fun `revoked access degrades silently to OFF without crashing (QA-A12)`() {
        gateway.add(FakeSession(ytm, track = song("a")))
        observer.start(preferredPackage = ytm)
        gateway.revoked = true

        gateway.sessionsChanged()

        val s = observer.snapshot.value
        assertThat(s.state).isEqualTo(ObservationState.OFF)
        assertThat(s.title).isNull()
        assertThat(s.hadGap).isTrue()
    }

    @Test
    fun `stopping releases every callback and forgets song memory (FR-A039, FR-A040, QA-A27)`() {
        val session = gateway.add(FakeSession(ytm, track = song("a")))
        observer.start(preferredPackage = ytm)
        observer.send(MusicCommand.PAUSE)

        observer.stop()

        assertThat(session.watched).isFalse()
        assertThat(gateway.openSessionWatchers).isEqualTo(0)
        val s = observer.snapshot.value
        assertThat(s.state).isEqualTo(ObservationState.IDLE)
        assertThat(s.title).isNull()
        assertThat(s.seen).isEmpty()
    }

    @Test
    fun `turning the switch off mid session releases at once (QA-A28)`() {
        val session = gateway.add(FakeSession(ytm, track = song("a")))
        observer.start(preferredPackage = ytm)

        countingOn = false
        observer.onSettingsChanged()

        assertThat(observer.snapshot.value.state).isEqualTo(ObservationState.OFF)
        assertThat(session.watched).isFalse()
    }

    @Test
    fun `commands go to the observed session only (FR-A054, QA-A23)`() {
        val podcast = gateway.add(FakeSession("podcast.app", playing = true))
        val music = gateway.add(FakeSession(ytm, track = song("a")))
        observer.start(preferredPackage = ytm)

        assertThat(observer.send(MusicCommand.PAUSE)).isTrue()

        assertThat(music.sent).containsExactly(MusicCommand.PAUSE)
        assertThat(podcast.sent).isEmpty()
    }

    @Test
    fun `no command without an observed session`() {
        observer.start(preferredPackage = ytm)

        assertThat(observer.send(MusicCommand.PAUSE)).isFalse()
    }

    @Test
    fun `the seen log lists the connected app, the current song and sent commands (SCR-13)`() {
        gateway.add(FakeSession(ytm, track = song("Slow Tide")))
        observer.start(preferredPackage = ytm)
        clock.advance(5_000)

        observer.send(MusicCommand.PAUSE)

        val kinds = observer.snapshot.value.seen.map { it.kind }
        assertThat(kinds).containsExactly(SeenKind.CONNECTED, SeenKind.TRACK, SeenKind.COMMAND_PAUSE).inOrder()
    }

    @Test
    fun `a new song is a track boundary, a republish of the same song is not`() {
        val session = gateway.add(FakeSession(ytm, track = song("a")))
        var boundaries = 0
        observer.onTrackBoundary = { boundaries++ }
        observer.start(preferredPackage = ytm)

        session.changeTrack("a")
        session.changeTrack("b")

        assertThat(boundaries).isEqualTo(1)
    }

    @Test
    fun `music paused for 10 seconds raises the hint once (FR-A042, QA-A24)`() {
        val session = gateway.add(FakeSession(ytm, track = song("a")))
        observer.start(preferredPackage = ytm)

        session.changePlaying(false)
        clock.advance(10_000)
        observer.onActiveTime()

        assertThat(observer.snapshot.value.showStoppedHint).isTrue()
        assertThat(observer.snapshot.value.isPlaying).isFalse()
    }

    @Test
    fun `the stopped hint goes away once the music plays again (emulator finding)`() {
        val session = gateway.add(FakeSession(ytm, track = song("a")))
        observer.start(preferredPackage = ytm)
        session.changePlaying(false)
        clock.advance(10_000)
        observer.onActiveTime()
        assertThat(observer.snapshot.value.showStoppedHint).isTrue()

        session.changePlaying(true)

        assertThat(observer.snapshot.value.showStoppedHint).isFalse()
    }

    @Test
    fun `starting mid session is labelled as started after (DEC-A13)`() {
        gateway.add(FakeSession(ytm, track = song("a")))
        activeMs = 300_000

        observer.start(preferredPackage = ytm)

        assertThat(observer.snapshot.value.startedMidSession).isTrue()
    }

    @Test
    fun `never observes this app's own session`() {
        gateway.add(FakeSession(own, track = song("noise", own)))

        observer.start(preferredPackage = null)

        assertThat(observer.snapshot.value.state).isEqualTo(ObservationState.SEARCHING)
    }

    @Test
    fun `turning off forgets titles and the seen log but keeps the integer count (review LOW-22)`() {
        gateway.add(FakeSession(ytm, track = song("Slow Tide")))
        observer.start(preferredPackage = ytm)
        activeMs = 40_000
        observer.onActiveTime()

        countingOn = false
        observer.onSettingsChanged()

        assertThat(observer.snapshot.value.seen).isEmpty()
        assertThat(observer.snapshot.value.title).isNull()
        assertThat(observer.snapshot.value.countedItems).isEqualTo(1)
    }

    @Test
    fun `a position rewind reported with playback changes counts a loop (review LOW-23)`() {
        val session = gateway.add(FakeSession(ytm, track = song("a"), position = 0))
        observer.start(preferredPackage = ytm)

        activeMs = 197_000
        session.position = 197_000
        session.changePlaying(true)
        activeMs = 200_000
        session.position = 1_000
        session.changePlaying(true)

        assertThat(observer.snapshot.value.loops).isEqualTo(1)
    }

    // ---- F18 M1 재생 기록 (DEC-A18) ----

    private val counted = mutableListOf<PlaylistEntry>()

    @Test
    fun `each counted song is reported once with title, artist and app, in play order with repeats (F18 M1)`() {
        observer.onSongCounted = { counted += it }
        val session = gateway.add(FakeSession(ytm, track = song("a")))
        observer.start(preferredPackage = ytm)

        activeMs = 200_000
        session.changeTrack("b", artist = "B")
        activeMs = 400_000
        session.changeTrack("a")
        activeMs = 430_000
        observer.onActiveTime()
        observer.onActiveTime()

        assertThat(counted).containsExactly(
            PlaylistEntry(1, "a", "A", Provider.YOUTUBE_MUSIC),
            PlaylistEntry(2, "b", "B", Provider.YOUTUBE_MUSIC),
            PlaylistEntry(3, "a", "A", Provider.YOUTUBE_MUSIC),
        ).inOrder()
    }

    @Test
    fun `the final count at finish reports a song that just reached 30 seconds, never one below it`() {
        observer.onSongCounted = { counted += it }
        gateway.add(FakeSession(ytm, track = song("a")))
        observer.start(preferredPackage = ytm)

        activeMs = 29_000
        assertThat(observer.observedCount()?.count).isEqualTo(0)
        activeMs = 31_000
        assertThat(observer.observedCount()?.count).isEqualTo(1)

        assertThat(counted.map { it.title }).containsExactly("a")
    }

    @Test
    fun `nothing is reported while counting is off (F18 M1)`() {
        countingOn = false
        observer.onSongCounted = { counted += it }
        val session = gateway.add(FakeSession(ytm, track = song("a")))
        observer.start(preferredPackage = ytm)

        activeMs = 200_000
        session.changeTrack("b")
        observer.onActiveTime()

        assertThat(counted).isEmpty()
    }

    @Test
    fun `nothing is reported without notification access (F18 M1)`() {
        gateway.granted = false
        observer.onSongCounted = { counted += it }
        gateway.add(FakeSession(ytm, track = song("a")))
        observer.start(preferredPackage = ytm)

        activeMs = 200_000
        observer.onActiveTime()

        assertThat(counted).isEmpty()
    }

    @Test
    fun `a failing playlist callback neither stops observation nor the count (MT-T7)`() {
        observer.onSongCounted = { throw IllegalStateException("disk") }
        gateway.add(FakeSession(ytm, track = song("a")))
        observer.start(preferredPackage = ytm)

        activeMs = 60_000
        observer.onActiveTime()

        assertThat(observer.snapshot.value.state).isEqualTo(ObservationState.OBSERVING)
        assertThat(observer.snapshot.value.countedItems).isEqualTo(1)
    }

    // ---- DEC-A21 앨범 아트 ----

    private val artA = byteArrayOf(1, 1)
    private val artB = byteArrayOf(2, 2)

    @Test
    fun `each counted song carries its own artwork, never the next song's, even when counted at the change (DEC-A21)`() {
        observer.onSongCounted = { counted += it }
        val session = gateway.add(FakeSession(ytm, track = song("a")).apply { art = artA })
        observer.start(preferredPackage = ytm)

        activeMs = 200_000
        session.changeTrack("b", artist = "B", art = artB) // "a" reaches 30 s inside this change
        activeMs = 400_000
        session.changeTrack("c") // "b" counted here; "c" has no artwork
        activeMs = 430_000
        observer.onActiveTime()

        assertThat(counted.map { it.title to it.artwork }).containsExactly(
            "a" to Artwork.of(artA),
            "b" to Artwork.of(artB),
            "c" to null,
        ).inOrder()
    }

    @Test
    fun `artwork published after the title (art loading republish) is attached to that song (DEC-A21)`() {
        observer.onSongCounted = { counted += it }
        val session = gateway.add(FakeSession(ytm, track = song("a")))
        observer.start(preferredPackage = ytm)

        session.changeTrack("a", durationMs = 200_000, art = artA)
        activeMs = 31_000
        observer.onActiveTime()

        assertThat(counted.single().artwork).isEqualTo(Artwork.of(artA))
    }

    @Test
    fun `a failing artwork read neither stops observation nor the count (DEC-A21, MT-T7)`() {
        observer.onSongCounted = { counted += it }
        gateway.add(FakeSession(ytm, track = song("a")).apply { artFails = true })
        observer.start(preferredPackage = ytm)

        activeMs = 31_000
        observer.onActiveTime()

        assertThat(observer.snapshot.value.state).isEqualTo(ObservationState.OBSERVING)
        assertThat(counted.map { it.title to it.artwork }).containsExactly("a" to null)
    }
}
