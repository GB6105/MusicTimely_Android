package com.gb6105.musictimely.data.media

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import com.gb6105.musictimely.service.MediaAccessListener

/**
 * Asks the notification service directly. NotificationManagerCompat caches the last value (a revoke could still
 * read as granted), and the secure setting string is written about a second after a grant (device test finding).
 */
internal fun isListenerEnabled(context: Context): Boolean =
    context.getSystemService(NotificationManager::class.java)
        .isNotificationListenerAccessGranted(ComponentName(context, MediaAccessListener::class.java))
