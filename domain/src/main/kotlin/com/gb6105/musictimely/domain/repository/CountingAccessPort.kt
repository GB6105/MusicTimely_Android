package com.gb6105.musictimely.domain.repository

/** Notification-access permission used for song counting (MT-A1·A2). */
interface CountingAccessPort {
    fun isGranted(): Boolean

    /** API 30+: the app's own listener detail page; otherwise the listener list (FR-A031). */
    fun openAccessSettings()
}
