package com.gb6105.musictimely.domain.repository

/** POST_NOTIFICATIONS (Android 13+). Asked once, in context, at the first start (FR-A063). */
interface NotificationPermissionPort {
    /** True when the OS requires a runtime grant that is not given yet. */
    fun needsRuntimeGrant(): Boolean
}
