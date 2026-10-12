package com.gb6105.musictimely.domain.model.analytics

import com.gb6105.musictimely.domain.model.control.MusicCommand
import com.gb6105.musictimely.domain.model.source.Provider
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** F12 분석 (SRS Android §10): 기본 OFF, 동의 시 허용 필드만, 곡·URL·패키지 원문 0. QA-31. */
class AnalyticsTest {

    private class RecordingSink : AnalyticsSink {
        val sent = mutableListOf<Pair<String, Map<String, String>>>()
        override fun send(name: String, fields: Map<String, String>) {
            sent += name to fields
        }
    }

    private val sink = RecordingSink()

    @Test
    fun `nothing is sent without consent (default OFF)`() {
        val analytics = Analytics(sink) { false }

        analytics.track(AnalyticsEvent.ControlSent(MusicCommand.PAUSE, origin = ControlOrigin.USER_BUTTON, applied = true))

        assertThat(sink.sent).isEmpty()
    }

    @Test
    fun `with consent each event carries only its allowed fields`() {
        val analytics = Analytics(sink) { true }

        analytics.track(AnalyticsEvent.PermissionPrompted(trigger = PromptTrigger.FIRST_EXTERNAL_SOURCE, result = PromptResult.LATER))
        analytics.track(AnalyticsEvent.ObservationState(Provider.MELON, quality = ObservationQuality.OBSERVED, reason = null))
        analytics.track(AnalyticsEvent.PlayerSwitched(Provider.YOUTUBE_MUSIC, Provider.MELON))
        analytics.track(AnalyticsEvent.FallbackToEstimate(FallbackReason.PERMISSION_REVOKED))
        analytics.track(AnalyticsEvent.ControlSent(MusicCommand.PLAY, ControlOrigin.USER_BUTTON, applied = false))
        analytics.track(AnalyticsEvent.StopAtEnd(StopOutcome.STOPPED, waitMs = 95_000))
        analytics.track(AnalyticsEvent.SourceRegistered(RegisterMethod.SHARE, Provider.SPOTIFY))

        assertThat(sink.sent).containsExactly(
            "mt_a_permission_prompted" to mapOf("trigger" to "first_external_source", "result" to "later"),
            "mt_a_observation_state" to mapOf("provider" to "MELON", "quality" to "observed"),
            "mt_a_player_switched" to mapOf("from_provider" to "YOUTUBE_MUSIC", "to_provider" to "MELON"),
            "mt_a_fallback_to_estimate" to mapOf("reason" to "permission_revoked"),
            "mt_a_control_sent" to mapOf("command" to "play", "origin" to "user_button", "applied" to "false"),
            "mt_a_stop_at_end" to mapOf("outcome" to "stopped", "wait_s" to "30-120"),
            "mt_source_registered" to mapOf("method" to "share", "provider" to "SPOTIFY"),
        ).inOrder()
    }

    @Test
    fun `waiting time is bucketed, never exact`() {
        assertThat(WaitBucket.of(10_000)).isEqualTo("0-30")
        assertThat(WaitBucket.of(30_000)).isEqualTo("30-120")
        assertThat(WaitBucket.of(200_000)).isEqualTo("120-360")
        assertThat(WaitBucket.of(400_000)).isEqualTo("360+")
    }
}
