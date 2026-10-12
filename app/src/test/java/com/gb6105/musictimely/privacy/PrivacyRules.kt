package com.gb6105.musictimely.privacy

data class SourceFile(val path: String, val text: String)

/** Source-level privacy rules (NFR-A013). Paths are relative to the app package root. */
object PrivacyRules {
    private val LISTENER_OVERRIDE = Regex("""override\s+fun\s+onNotification(Posted|Removed)\b""")
    private val PLAY_CALL = Regex("""transportControls\s*\.\s*play\s*\(""")
    private val TRANSPORT_CALL = Regex("""transportControls\s*\.\s*(\w+)\s*\(""")
    private val ALLOWED_TRANSPORT = setOf("play", "pause")
    private val TAP_CALL = Regex("""(?<!fun )\bonTransportTap\s*\(\s*\)""")
    private val SEND_CALL = Regex("""observer\s*\.\s*send\s*\(([^)]*)\)""")
    private val MEDIA_LEAK = Regex("""import\s+(android\.util\.Log|java\.net\.|okhttp3\.|retrofit2\.)|\bLog\.[vdiwe]\(|\bprintln\(""")
    /** Song titles and album art (DEC-A18, DEC-A21). */
    private val SONG_DATA = Regex("""\b(PlaylistEntry|TrackMetadata|Artwork)\b""")
    private val ARTWORK = Regex("""\bArtwork\b""")
    private val ART_URI = Regex("""METADATA_KEY_(ALBUM_ART|ART|DISPLAY_ICON)_URI""")
    private val SHARE_OUT = Regex("""\bACTION_SEND(_MULTIPLE)?\b|\bFileProvider\b|\bcreateChooser\b""")
    private val XML_COMMENT = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
    private val FORBIDDEN_MANIFEST = listOf(
        "SCHEDULE_EXACT_ALARM", "USE_EXACT_ALARM", "QUERY_ALL_PACKAGES", "READ_MEDIA_AUDIO",
        "MEDIA_CONTENT_CONTROL", "BIND_ACCESSIBILITY_SERVICE", "specialUse",
    )

    /** FR-A032: the listener must not handle notification content. */
    fun listenerOverrides(files: List<SourceFile>): List<String> =
        files.filter { LISTENER_OVERRIDE.containsMatchIn(it.text) }.map { it.path }

    /** FR-A045: `play()` may be called from the user button handler only. */
    fun playCallSites(files: List<SourceFile>): List<String> =
        files.flatMap { file -> PLAY_CALL.findAll(file.text).map { "${file.path}@${it.range.first}" }.toList() }

    /** FR-A046: no TransportControls method other than play and pause. */
    fun forbiddenTransportCalls(files: List<SourceFile>): List<String> = files.flatMap { file ->
        TRANSPORT_CALL.findAll(file.text).map { it.groupValues[1] }.filterNot { it in ALLOWED_TRANSPORT }.map { "${file.path}: $it" }.toList()
    }

    /** FR-A038·NFR-A008: the media observation package neither logs nor talks to the network. */
    fun mediaLeaks(files: List<SourceFile>): List<String> =
        files.filter { it.path.startsWith("data/media/") && MEDIA_LEAK.containsMatchIn(it.text) }.map { it.path }

    /** DEC-A18·A21: data-layer files outside `data/media/` that touch song titles or art; only the playlist store may. */
    fun songPersisters(files: List<SourceFile>): List<String> = files
        .filter { it.path.startsWith("data/") && !it.path.startsWith("data/media/") && SONG_DATA.containsMatchIn(it.text) }
        .map { it.path }

    /** DEC-A18: no file that handles playlist entries logs or talks to the network. */
    fun songLeaks(files: List<SourceFile>): List<String> =
        files.filter { SONG_DATA.containsMatchIn(it.text) && MEDIA_LEAK.containsMatchIn(it.text) }.map { it.path }

    /** DEC-A21: album art comes from metadata bitmaps only, never by fetching a `*_URI`. */
    fun artworkUriReads(files: List<SourceFile>): List<String> =
        files.filter { ART_URI.containsMatchIn(it.text) }.map { it.path }

    /** DEC-A21 / plan M5: album art never goes into a share image until the rights review. */
    fun artworkSharers(files: List<SourceFile>): List<String> =
        files.filter { ARTWORK.containsMatchIn(it.text) && SHARE_OUT.containsMatchIn(it.text) }.map { it.path }

    /** Every `send(...)` that is not a literal PAUSE, as "path: argument"; each one is a path that may send play. */
    fun nonPauseSends(files: List<SourceFile>): List<String> = files.flatMap { file ->
        SEND_CALL.findAll(file.text).map { it.groupValues[1].trim() }.filterNot { it == "MusicCommand.PAUSE" }.map { "${file.path}: $it" }.toList()
    }

    /** Callers of the user tap entry point (`onTransportTap()` calls, not its declarations). */
    fun transportTapCallers(files: List<SourceFile>): List<String> =
        files.flatMap { file -> TAP_CALL.findAll(file.text).map { file.path }.toList() }

    /** SRS Android §7.1 "선언하지 않는 것". */
    fun forbiddenManifestEntries(manifest: String): List<String> {
        val declarations = manifest.replace(XML_COMMENT, "")
        return FORBIDDEN_MANIFEST.filter { it in declarations }
    }
}
