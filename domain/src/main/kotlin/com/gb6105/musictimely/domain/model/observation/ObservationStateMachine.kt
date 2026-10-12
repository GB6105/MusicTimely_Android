package com.gb6105.musictimely.domain.model.observation

/** SRS Android §5.2. Observation state never changes the timer state. */
enum class ObservationState {
    OFF, IDLE, SEARCHING, OBSERVING, DEGRADED;

    /** Only OBSERVING shows titles and counts; every other state shows D0 (FR-A037). */
    val showsObservedData: Boolean get() = this == OBSERVING

    /** States that keep media-session callbacks registered; READY / ENDED release them (FR-A039). */
    val holdsCallbacks: Boolean get() = this == SEARCHING || this == OBSERVING || this == DEGRADED
}

sealed interface ObservationEvent {
    data class Enabled(val permission: Boolean, val setting: Boolean) : ObservationEvent
    data object SessionRunning : ObservationEvent
    data object TargetFound : ObservationEvent
    data class TargetLost(val alternativePlaying: Boolean) : ObservationEvent
    data object MetadataMissingFor60s : ObservationEvent
    data object ListenerDisconnected : ObservationEvent
    data object CallbackResumed : ObservationEvent
    data object PermissionRevoked : ObservationEvent
    data object SettingTurnedOff : ObservationEvent
    data object SessionEnded : ObservationEvent
}

object ObservationStateMachine {
    fun next(state: ObservationState, event: ObservationEvent): ObservationState = when (event) {
        ObservationEvent.PermissionRevoked, ObservationEvent.SettingTurnedOff -> ObservationState.OFF
        is ObservationEvent.Enabled -> when {
            !(event.permission && event.setting) -> ObservationState.OFF
            state == ObservationState.OFF -> ObservationState.IDLE
            else -> state
        }
        ObservationEvent.SessionRunning -> if (state == ObservationState.IDLE) ObservationState.SEARCHING else state
        ObservationEvent.TargetFound -> if (state == ObservationState.SEARCHING) ObservationState.OBSERVING else state
        is ObservationEvent.TargetLost -> when {
            state != ObservationState.OBSERVING && state != ObservationState.DEGRADED -> state
            event.alternativePlaying -> ObservationState.OBSERVING
            else -> ObservationState.SEARCHING
        }
        ObservationEvent.MetadataMissingFor60s ->
            if (state == ObservationState.OBSERVING) ObservationState.DEGRADED else state
        ObservationEvent.ListenerDisconnected ->
            if (state == ObservationState.OBSERVING || state == ObservationState.SEARCHING) ObservationState.DEGRADED else state
        ObservationEvent.CallbackResumed -> if (state == ObservationState.DEGRADED) ObservationState.OBSERVING else state
        ObservationEvent.SessionEnded -> if (state.holdsCallbacks) ObservationState.IDLE else state
    }
}
