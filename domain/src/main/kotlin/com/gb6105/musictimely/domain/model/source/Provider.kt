package com.gb6105.musictimely.domain.model.source

/** Supported music apps (SRS Android §7.1 `<queries>`). Package names are re-checked on the Play Store before release. */
enum class Provider(val packageName: String?) {
    YOUTUBE_MUSIC("com.google.android.apps.youtube.music"),
    SPOTIFY("com.spotify.music"),
    MELON("com.iloen.melon"),
    GENIE("com.ktmusic.geniemusic"),
    FLO("skplanet.musicmate"),
    APPLE_MUSIC("com.apple.android.music"),
    OTHER(null),
    ;

    companion object {
        fun fromPackage(packageName: String): Provider = entries.firstOrNull { it.packageName == packageName } ?: OTHER
    }
}

/** A saved external music source (SRS Android §9.1). [linkUri] is null for app-only sources. */
data class ExternalSource(
    val id: String,
    val provider: Provider,
    val packageName: String,
    val linkUri: String?,
    val label: String,
    val createdAtEpochMs: Long,
)
