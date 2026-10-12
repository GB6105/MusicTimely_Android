package com.gb6105.musictimely.data.notification

/**
 * DEC-A15 (Android 14+): the user may swipe the ongoing progress notification away. The session continues;
 * the notification is re-posted only when the app is opened again, never from the background.
 */
class ProgressDismissal {
    @Volatile private var dismissed = false

    val isDismissed: Boolean get() = dismissed

    fun markDismissed() {
        dismissed = true
    }

    fun reset() {
        dismissed = false
    }
}
