package com.gb6105.musictimely.data.media

import android.app.Application
import android.content.ComponentName
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gb6105.musictimely.service.MediaAccessListener
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** FR-A031: [켜기] → 알림 접근 상세 설정 (API 30+). */
@RunWith(AndroidJUnit4::class)
class AndroidCountingAccessIntegrationTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val access = AndroidCountingAccess(app)

    @Test
    fun `opens this app's notification listener detail page`() {
        access.openAccessSettings()

        val intent = shadowOf(app).nextStartedActivity
        assertThat(intent.action).isEqualTo(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
        assertThat(intent.getStringExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME))
            .isEqualTo(ComponentName(app, MediaAccessListener::class.java).flattenToString())
    }

    @Test
    fun `asks the notification service directly, so a grant or revoke is seen at once (device test finding)`() {
        val component = ComponentName(app, MediaAccessListener::class.java)
        val notifications = shadowOf(app.getSystemService(android.app.NotificationManager::class.java))
        assertThat(access.isGranted()).isFalse()

        notifications.setNotificationListenerAccessGranted(component, true)
        assertThat(access.isGranted()).isTrue()

        notifications.setNotificationListenerAccessGranted(component, false)
        assertThat(access.isGranted()).isFalse()
    }
}
