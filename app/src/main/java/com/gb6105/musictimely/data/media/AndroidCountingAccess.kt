package com.gb6105.musictimely.data.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.gb6105.musictimely.domain.repository.CountingAccessPort
import com.gb6105.musictimely.service.MediaAccessListener

/** Notification access for song counting (SRS Android §7.2): detail page on API 30+, the list otherwise. */
class AndroidCountingAccess(private val context: Context) : CountingAccessPort {

    override fun isGranted(): Boolean = isListenerEnabled(context)

    override fun openAccessSettings() {
        val component = ComponentName(context, MediaAccessListener::class.java).flattenToString()
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component)
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
