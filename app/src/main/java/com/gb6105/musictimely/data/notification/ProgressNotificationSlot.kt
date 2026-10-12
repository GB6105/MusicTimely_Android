package com.gb6105.musictimely.data.notification

import android.app.Notification

/**
 * The live session progress notification, shared so the mediaPlayback service rides on it instead of
 * posting a second one (SRS Android §7.6). Null when no session progress is shown.
 */
class ProgressNotificationSlot {
    @Volatile var latest: Notification? = null
        internal set

    @androidx.annotation.VisibleForTesting
    fun setForTest(notification: Notification?) {
        latest = notification
    }
}
