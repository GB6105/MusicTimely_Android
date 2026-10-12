package com.gb6105.musictimely.domain.model.observation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 관측 상태 머신 (SRS Android §5.2), FR-A030·A037·A039·A041, QA-A12·A28. */
class ObservationStateMachineTest {

    private fun ObservationState.on(event: ObservationEvent) = ObservationStateMachine.next(this, event)

    @Test
    fun `off until both permission and the in-app switch are on`() {
        assertThat(ObservationState.OFF.on(ObservationEvent.Enabled(permission = true, setting = false))).isEqualTo(ObservationState.OFF)
        assertThat(ObservationState.OFF.on(ObservationEvent.Enabled(permission = false, setting = true))).isEqualTo(ObservationState.OFF)
        assertThat(ObservationState.OFF.on(ObservationEvent.Enabled(permission = true, setting = true))).isEqualTo(ObservationState.IDLE)
    }

    @Test
    fun `a running session searches, a found target observes`() {
        val searching = ObservationState.IDLE.on(ObservationEvent.SessionRunning)

        assertThat(searching).isEqualTo(ObservationState.SEARCHING)
        assertThat(searching.on(ObservationEvent.TargetFound)).isEqualTo(ObservationState.OBSERVING)
    }

    @Test
    fun `losing the target hands off when another session plays, else searches again`() {
        assertThat(ObservationState.OBSERVING.on(ObservationEvent.TargetLost(alternativePlaying = true))).isEqualTo(ObservationState.OBSERVING)
        assertThat(ObservationState.OBSERVING.on(ObservationEvent.TargetLost(alternativePlaying = false))).isEqualTo(ObservationState.SEARCHING)
    }

    @Test
    fun `missing metadata or a disconnected listener degrades, a normal callback recovers`() {
        val degraded = ObservationState.OBSERVING.on(ObservationEvent.MetadataMissingFor60s)

        assertThat(degraded).isEqualTo(ObservationState.DEGRADED)
        assertThat(ObservationState.OBSERVING.on(ObservationEvent.ListenerDisconnected)).isEqualTo(ObservationState.DEGRADED)
        assertThat(degraded.on(ObservationEvent.CallbackResumed)).isEqualTo(ObservationState.OBSERVING)
    }

    @Test
    fun `revoking permission or turning the switch off goes OFF from any state (QA-A12, QA-A28)`() {
        ObservationState.entries.forEach { state ->
            assertThat(state.on(ObservationEvent.PermissionRevoked)).isEqualTo(ObservationState.OFF)
            assertThat(state.on(ObservationEvent.SettingTurnedOff)).isEqualTo(ObservationState.OFF)
        }
    }

    @Test
    fun `ending the session releases back to IDLE (FR-A039)`() {
        listOf(ObservationState.SEARCHING, ObservationState.OBSERVING, ObservationState.DEGRADED).forEach {
            assertThat(it.on(ObservationEvent.SessionEnded)).isEqualTo(ObservationState.IDLE)
        }
    }

    @Test
    fun `only OBSERVING shows observed data, everything else falls back to D0 (FR-A037)`() {
        assertThat(ObservationState.entries.filter { it.showsObservedData }).containsExactly(ObservationState.OBSERVING)
        assertThat(ObservationState.entries.filter { it.holdsCallbacks })
            .containsExactly(ObservationState.SEARCHING, ObservationState.OBSERVING, ObservationState.DEGRADED)
    }

    @Test
    fun `unrelated events keep the state`() {
        assertThat(ObservationState.IDLE.on(ObservationEvent.TargetFound)).isEqualTo(ObservationState.IDLE)
        assertThat(ObservationState.OFF.on(ObservationEvent.SessionRunning)).isEqualTo(ObservationState.OFF)
    }
}
