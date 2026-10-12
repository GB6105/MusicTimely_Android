package com.gb6105.musictimely.service

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import com.gb6105.musictimely.data.media.ObservationCoordinator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Exists only so the user can grant notification access for media-session observation (MT-A1).
 * Notification content is never handled: no onNotificationPosted/Removed overrides (MT-A3, FR-A032).
 * A lost binding asks for one rebind and degrades observation until it reconnects (OBS_LISTENER_DISCONNECTED).
 */
@AndroidEntryPoint
class MediaAccessListener : NotificationListenerService() {

    @Inject lateinit var coordinator: ObservationCoordinator

    override fun onListenerConnected() {
        super.onListenerConnected()
        coordinator.onListenerConnected()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        coordinator.onListenerDisconnected()
        // One request per disconnect; the system decides when to bind again.
        runCatching { requestRebind(ComponentName(this, MediaAccessListener::class.java)) }
    }
}
