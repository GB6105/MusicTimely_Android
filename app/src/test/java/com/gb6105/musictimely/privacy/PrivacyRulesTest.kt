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
