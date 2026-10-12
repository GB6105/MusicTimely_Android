package com.gb6105.musictimely.privacy

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * F11 정적 검사 (NFR-A013, FR-A032·A045·A046, SRS Android §7.1).
 * The rule functions are proven against violating samples first; then the real sources must pass.
 */
class PrivacyRulesTest {

    // ---- rules catch violations (samples) ----

    @Test
    fun `listener override of notification callbacks is a violation`() {
        val sample = SourceFile("service/X.kt", "class X : NotificationListenerService() {\n override fun onNotificationPosted(sbn: StatusBarNotification) {}\n}")

        assertThat(PrivacyRules.listenerOverrides(listOf(sample))).containsExactly("service/X.kt")
    }

    @Test
    fun `more than one play call site is a violation`() {
        val a = SourceFile("a.kt", "controller.transportControls.play()")
        val b = SourceFile("b.kt", "transportControls.play()")

        assertThat(PrivacyRules.playCallSites(listOf(a, b))).hasSize(2)
        assertThat(PrivacyRules.playCallSites(listOf(SourceFile("c.kt", "player.play()")))).isEmpty()
    }

    @Test
    fun `transport controls other than play and pause are violations`() {
        val sample = SourceFile("a.kt", "transportControls.skipToNext()\ncontroller.transportControls.seekTo(0)\ntransportControls.pause()")

        assertThat(PrivacyRules.forbiddenTransportCalls(listOf(sample))).containsExactly("a.kt: skipToNext", "a.kt: seekTo")
    }

    @Test
    fun `logging or network in the media package is a violation`() {
        val log = SourceFile("data/media/A.kt", "import android.util.Log\nLog.d(\"t\", title)")
        val net = SourceFile("data/media/B.kt", "import java.net.URL")
        val elsewhere = SourceFile("data/local/C.kt", "import android.util.Log")

        assertThat(PrivacyRules.mediaLeaks(listOf(log, net, elsewhere))).containsExactly("data/media/A.kt", "data/media/B.kt")
    }

    @Test
    fun `forbidden manifest declarations are violations`() {
        val manifest = """<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
            <service android:foregroundServiceType="specialUse" />"""

        assertThat(PrivacyRules.forbiddenManifestEntries(manifest)).containsExactly("SCHEDULE_EXACT_ALARM", "specialUse")
    }

    @Test
    fun `xml comments are not declarations`() {
        val manifest = "<!-- DEC-A08: supported apps only, no QUERY_ALL_PACKAGES -->\n<uses-permission android:name=\"android.permission.POST_NOTIFICATIONS\" />"

        assertThat(PrivacyRules.forbiddenManifestEntries(manifest)).isEmpty()
    }

    @Test
    fun `play may only be sent through the user tap path (review MEDIUM-18)`() {
        val tap = SourceFile("data/media/ObservationCoordinator.kt", "override fun onTransportTap() = serial.run {\n observer.send(command)\n}")
        val sneaky = SourceFile("data/media/Other.kt", "observer.send(MusicCommand.PLAY)")
        val pause = SourceFile("data/media/Stop.kt", "observer.send(MusicCommand.PAUSE)")

        assertThat(PrivacyRules.nonPauseSends(listOf(tap, sneaky, pause)))
            .containsExactly("data/media/ObservationCoordinator.kt: command", "data/media/Other.kt: MusicCommand.PLAY")
    }

    @Test
    fun `the user tap entry point has exactly one caller (review 2 H4, T10)`() {
        val ok = SourceFile("ui/session/SessionViewModel.kt", "music.onTransportTap()")
        val extra = SourceFile("ui/session/Other.kt", "music.onTransportTap()")

        assertThat(PrivacyRules.transportTapCallers(listOf(ok, extra))).containsExactly("ui/session/SessionViewModel.kt", "ui/session/Other.kt")
    }

    @Test
    fun `song titles stored outside the playlist store are violations (DEC-A18)`() {
        val store = SourceFile("data/local/FilePlaylistStore.kt", "fun append(entry: PlaylistEntry)")
        val prefs = SourceFile("data/local/Prefs.kt", "putString(\"t\", entry.title) // PlaylistEntry")
        val media = SourceFile("data/media/MediaObserver.kt", "TrackMetadata")
        val ui = SourceFile("ui/result/ResultScreen.kt", "PlaylistEntry")

        assertThat(PrivacyRules.songPersisters(listOf(store, prefs, media, ui))).containsExactly("data/local/FilePlaylistStore.kt", "data/local/Prefs.kt")
    }

    @Test
    fun `logging or network next to playlist entries is a violation (DEC-A18)`() {
        val logged = SourceFile("ui/result/X.kt", "import android.util.Log\nfun f(e: PlaylistEntry) = Log.d(\"t\", e.title)")
        val clean = SourceFile("ui/result/Y.kt", "fun f(e: PlaylistEntry) = e.title")

        assertThat(PrivacyRules.songLeaks(listOf(logged, clean))).containsExactly("ui/result/X.kt")
    }

    @Test
    fun `album art stored outside the playlist store is a violation like a title (DEC-A21)`() {
        val store = SourceFile("data/local/FilePlaylistStore.kt", "val a: Artwork?")
        val cache = SourceFile("data/local/ArtCache.kt", "fun put(a: Artwork) = file.writeBytes(a.bytes)")
        val media = SourceFile("data/media/ArtworkEncoder.kt", "Artwork.MAX_BYTES")
        val encoderName = SourceFile("data/local/Other.kt", "ArtworkEncoder.MAX_EDGE_PX")

        assertThat(PrivacyRules.songPersisters(listOf(store, cache, media, encoderName)))
            .containsExactly("data/local/FilePlaylistStore.kt", "data/local/ArtCache.kt")
    }

    @Test
    fun `logging next to album art is a violation (DEC-A21)`() {
        val logged = SourceFile("ui/component/X.kt", "fun f(a: Artwork) = println(a)")

        assertThat(PrivacyRules.songLeaks(listOf(logged))).containsExactly("ui/component/X.kt")
    }

    @Test
    fun `reading album art by uri is a violation (DEC-A21)`() {
        val uri = SourceFile("data/media/A.kt", "m.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)")
        val icon = SourceFile("data/media/B.kt", "METADATA_KEY_DISPLAY_ICON_URI")
        val bitmap = SourceFile("data/media/C.kt", "m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)")

        assertThat(PrivacyRules.artworkUriReads(listOf(uri, icon, bitmap))).containsExactly("data/media/A.kt", "data/media/B.kt")
    }

    @Test
    fun `album art next to a share intent or file provider is a violation (DEC-A21, plan M5)`() {
        val share = SourceFile("ui/share/ShareImage.kt", "fun f(a: Artwork) = Intent(Intent.ACTION_SEND)")
        val provider = SourceFile("ui/share/Card.kt", "Artwork; FileProvider.getUriForFile(c, x, f)")
        val inApp = SourceFile("ui/result/ResultScreen.kt", "CoverTile(entry.artwork as Artwork?)")
        val receive = SourceFile("ui/share/ShareReceiveActivity.kt", "Intent.ACTION_SEND")

        assertThat(PrivacyRules.artworkSharers(listOf(share, provider, inApp, receive)))
            .containsExactly("ui/share/ShareImage.kt", "ui/share/Card.kt")
    }

    // ---- the real code base ----

    private val sources: List<SourceFile> = File("src/main/java").walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .map { SourceFile(it.relativeTo(File("src/main/java/com/gb6105/musictimely")).path, it.readText()) }
        .toList()

    @Test
    fun `the app sources are scanned`() {
        assertThat(sources.map { it.path }).contains("MainActivity.kt")
    }

    @Test
    fun `app sources follow the privacy rules`() {
        assertThat(PrivacyRules.listenerOverrides(sources)).isEmpty()
        assertThat(PrivacyRules.playCallSites(sources)).hasSize(1)
        // DEC-A16: the user tap (`command`) and resuming music this app paused with the timer (`MusicCommand.PLAY`).
        assertThat(PrivacyRules.nonPauseSends(sources))
            .containsExactly("data/media/ObservationCoordinator.kt: command", "data/media/ObservationCoordinator.kt: MusicCommand.PLAY")
        assertThat(PrivacyRules.transportTapCallers(sources)).containsExactly("ui/session/SessionViewModel.kt")
        assertThat(PrivacyRules.forbiddenTransportCalls(sources)).isEmpty()
        assertThat(PrivacyRules.mediaLeaks(sources)).isEmpty()
        assertThat(PrivacyRules.songPersisters(sources)).containsExactly("data/local/FilePlaylistStore.kt")
        assertThat(PrivacyRules.songLeaks(sources)).isEmpty()
        assertThat(PrivacyRules.artworkUriReads(sources)).isEmpty()
        assertThat(PrivacyRules.artworkSharers(sources)).isEmpty()
    }

    @Test
    fun `album art is scanned where it is read, stored and shown (DEC-A21)`() {
        val artFiles = sources.filter { Regex("""\bArtwork\b""").containsMatchIn(it.text) }.map { it.path }

        assertThat(artFiles).containsAtLeast("data/media/ArtworkEncoder.kt", "data/local/FilePlaylistStore.kt", "ui/component/CoverMotif.kt")
    }

    @Test
    fun `no analytics or crash SDK is linked (SRS Android 10, F12)`() {
        val build = File("build.gradle.kts").readText() + File("../gradle/libs.versions.toml").readText()

        listOf("firebase", "crashlytics", "analytics", "amplitude", "mixpanel", "sentry", "clarity").forEach {
            assertThat(build.lowercase()).doesNotContain(it)
        }
    }

    @Test
    fun `the manifest declares nothing forbidden`() {
        assertThat(PrivacyRules.forbiddenManifestEntries(File("src/main/AndroidManifest.xml").readText())).isEmpty()
    }
}
