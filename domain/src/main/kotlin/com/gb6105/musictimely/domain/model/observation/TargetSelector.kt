package com.gb6105.musictimely.domain.model.observation

data class MediaSessionInfo(val packageName: String, val isPlaying: Boolean)

/** DEC-A02: the chosen source app's session first, else the first playing session in list order; never this app. */
object TargetSelector {
    fun select(preferredPackage: String?, sessions: List<MediaSessionInfo>, ownPackage: String): MediaSessionInfo? {
        val candidates = sessions.filterNot { it.packageName == ownPackage }
        return candidates.firstOrNull { it.packageName == preferredPackage } ?: candidates.firstOrNull { it.isPlaying }
    }
}
