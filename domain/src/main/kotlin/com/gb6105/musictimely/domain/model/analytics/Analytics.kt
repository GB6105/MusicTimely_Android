package com.gb6105.musictimely.domain.model.analytics

import com.gb6105.musictimely.domain.model.control.MusicCommand
import com.gb6105.musictimely.domain.model.source.Provider

enum class PromptTrigger { FIRST_EXTERNAL_SOURCE, PANEL_LINK, SETTINGS }
enum class PromptResult { TURN_ON, LATER }
enum class ObservationQuality { OBSERVED, DEGRADED, NONE }
enum class FallbackReason { NO_SESSION, NO_METADATA, LISTENER_DISCONNECTED, PERMISSION_REVOKED }
enum class ControlOrigin { USER_BUTTON, STOP_AT_END }
enum class StopOutcome { STOPPED, CANCELLED, FAILED }
enum class RegisterMethod { SHARE, PASTE, APP_ONLY }

sealed interface AnalyticsEvent {
    data class PermissionPrompted(val trigger: PromptTrigger, val result: PromptResult) : AnalyticsEvent
    data class ObservationState(val provider: Provider, val quality: ObservationQuality, val reason: FallbackReason?) : AnalyticsEvent
    data class PlayerSwitched(val from: Provider, val to: Provider) : AnalyticsEvent
    data class FallbackToEstimate(val reason: FallbackReason) : AnalyticsEvent
    data class ControlSent(val command: MusicCommand, val origin: ControlOrigin, val applied: Boolean) : AnalyticsEvent
    data class StopAtEnd(val outcome: StopOutcome, val waitMs: Long) : AnalyticsEvent
    data class SourceRegistered(val method: RegisterMethod, val provider: Provider) : AnalyticsEvent
}

fun interface AnalyticsSink {
    fun send(name: String, fields: Map<String, String>)
}

/** Waiting time is reported only as a bucket. */
object WaitBucket {
    fun of(waitMs: Long): String = when {
        waitMs < 30_000 -> "0-30"
        waitMs < 120_000 -> "30-120"
        waitMs < 360_000 -> "120-360"
        else -> "360+"
    }
}

/**
 * SRS Android §10: analytics are OFF unless consented, and each event maps to its allowlisted fields only.
 * Fields are built from enums and booleans, so titles, artists, URLs, package names and song counts cannot
 * reach a sink by construction.
 */
class Analytics(private val sink: AnalyticsSink, private val consented: () -> Boolean) {
    fun track(event: AnalyticsEvent) {
        if (!consented()) return
        val (name, fields) = when (event) {
            is AnalyticsEvent.PermissionPrompted ->
                "mt_a_permission_prompted" to mapOf("trigger" to event.trigger.key(), "result" to event.result.key())
            is AnalyticsEvent.ObservationState ->
                "mt_a_observation_state" to buildMap {
                    put("provider", event.provider.name)
                    put("quality", event.quality.key())
                    event.reason?.let { put("reason", it.key()) }
                }
            is AnalyticsEvent.PlayerSwitched ->
                "mt_a_player_switched" to mapOf("from_provider" to event.from.name, "to_provider" to event.to.name)
            is AnalyticsEvent.FallbackToEstimate -> "mt_a_fallback_to_estimate" to mapOf("reason" to event.reason.key())
            is AnalyticsEvent.ControlSent -> "mt_a_control_sent" to mapOf(
                "command" to event.command.key(), "origin" to event.origin.key(), "applied" to event.applied.toString(),
            )
            is AnalyticsEvent.StopAtEnd ->
                "mt_a_stop_at_end" to mapOf("outcome" to event.outcome.key(), "wait_s" to WaitBucket.of(event.waitMs))
            is AnalyticsEvent.SourceRegistered ->
                "mt_source_registered" to mapOf("method" to event.method.key(), "provider" to event.provider.name)
        }
        sink.send(name, fields)
    }

    private fun Enum<*>.key() = name.lowercase()
}
