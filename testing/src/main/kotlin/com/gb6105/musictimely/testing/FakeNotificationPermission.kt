package com.gb6105.musictimely.testing

import com.gb6105.musictimely.domain.repository.NotificationPermissionPort

class FakeNotificationPermission(var needsGrant: Boolean = false) : NotificationPermissionPort {
    override fun needsRuntimeGrant() = needsGrant
}
