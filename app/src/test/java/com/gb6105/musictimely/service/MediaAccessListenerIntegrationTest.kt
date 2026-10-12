package com.gb6105.musictimely.service

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.shadows.ShadowNotificationListenerService

/** OBS_LISTENER_DISCONNECTED → requestRebind 1회 (SRS Android §7.2). Notification content is never read. */
@RunWith(AndroidJUnit4::class)
class MediaAccessListenerIntegrationTest {

    @Before
    fun reset() = ShadowNotificationListenerService.reset()

    @Test
    fun `a disconnect asks the system to rebind once`() {
        val listener = Robolectric.buildService(MediaAccessListener::class.java).create().get()

        listener.onListenerDisconnected()

        assertThat(ShadowNotificationListenerService.getRebindRequestCount()).isEqualTo(1)
    }

    @Test
    fun `connecting again does not ask for another rebind`() {
        val listener = Robolectric.buildService(MediaAccessListener::class.java).create().get()

        listener.onListenerDisconnected()
        listener.onListenerConnected()

        assertThat(ShadowNotificationListenerService.getRebindRequestCount()).isEqualTo(1)
    }
}
