package com.gb6105.musictimely.domain.model.observation

import com.gb6105.musictimely.domain.model.source.Provider

enum class SeenKind { CONNECTED, TRACK, COMMAND_PLAY, COMMAND_PAUSE }

/** "이 앱이 본 것" log entry (SCR-13). Memory only; cleared when the session ends (FR-A040). */
data class SeenEntry(val atMonoMs: Long, val kind: SeenKind, val provider: Provider, val title: String?, val artist: String?)

/** What song counting currently shows. Titles exist only while OBSERVING (FR-A018). */
data class ObservationSnapshot(
    val state: ObservationState = ObservationState.OFF,
    val provider: Provider? = null,
    val title: String? = null,
    val isPlaying: Boolean = false,
    val countedItems: Int = 0,
    val currentIndex: Int = 1,
    val loops: Int = 0,
    val startedMidSession: Boolean = false,
    val observedFromActiveMs: Long = 0,
    val hadGap: Boolean = false,
    val handedOff: Boolean = false,
    val showStoppedHint: Boolean = false,
    val seen: List<SeenEntry> = emptyList(),
)
