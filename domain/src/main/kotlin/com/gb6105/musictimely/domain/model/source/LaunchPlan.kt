package com.gb6105.musictimely.domain.model.source

sealed interface LaunchStep {
    data class ViewLinkInPackage(val url: String, val packageName: String) : LaunchStep
    data class LaunchApp(val packageName: String) : LaunchStep
    data class OpenStoreListing(val packageName: String) : LaunchStep
    data object ShowManualGuide : LaunchStep
}

/**
 * FR-A026 fallback order (SRS Android §7.4). Each step is tried only when the previous one fails.
 * A successful open is not evidence of playback.
 */
object LaunchPlan {
    fun of(source: ExternalSource): List<LaunchStep> = buildList {
        source.linkUri?.let { add(LaunchStep.ViewLinkInPackage(it, source.packageName)) }
        add(LaunchStep.LaunchApp(source.packageName))
        add(LaunchStep.OpenStoreListing(source.packageName))
        add(LaunchStep.ShowManualGuide)
    }
}
