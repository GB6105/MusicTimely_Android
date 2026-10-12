package com.gb6105.musictimely.data.launcher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.gb6105.musictimely.domain.model.source.ExternalSource
import com.gb6105.musictimely.domain.model.source.LaunchPlan
import com.gb6105.musictimely.domain.model.source.LaunchStep
import com.gb6105.musictimely.domain.model.source.Provider
import com.gb6105.musictimely.domain.repository.LaunchOutcome
import com.gb6105.musictimely.domain.repository.MusicLauncherPort

/**
 * Executes the FR-A026 fallback plan. Opening is a request, not proof of playback (SRS Android §7.4);
 * a failure never touches the timer (FR-A027). Package visibility comes from `<queries>` only (DEC-A08).
 */
class ExternalLauncher(private val context: Context) : MusicLauncherPort {

    override fun open(source: ExternalSource): LaunchOutcome {
        LaunchPlan.of(source).forEach { step ->
            when (step) {
                is LaunchStep.ViewLinkInPackage ->
                    if (start(Intent(Intent.ACTION_VIEW, Uri.parse(step.url)).setPackage(step.packageName))) return LaunchOutcome.OPENED_LINK
                is LaunchStep.LaunchApp ->
                    if (context.packageManager.getLaunchIntentForPackage(step.packageName)?.let(::start) == true) return LaunchOutcome.OPENED_APP
                is LaunchStep.OpenStoreListing ->
                    if (start(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${step.packageName}")))) return LaunchOutcome.OPENED_STORE
                LaunchStep.ShowManualGuide -> return LaunchOutcome.MANUAL
            }
        }
        return LaunchOutcome.MANUAL
    }

    /** Supported apps that are installed, in [Provider] order. */
    override fun installedProviders(): List<Provider> = Provider.entries.filter { provider ->
        provider.packageName?.let { context.packageManager.getLaunchIntentForPackage(it) } != null
    }

    private fun start(intent: Intent): Boolean {
        if (intent.resolveActivity(context.packageManager) == null) return false
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
