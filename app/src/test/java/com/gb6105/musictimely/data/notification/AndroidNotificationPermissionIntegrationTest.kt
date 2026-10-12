package com.gb6105.musictimely.data.notification

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** FR-A063: Android 13+ runtime grant. */
@RunWith(AndroidJUnit4::class)
class AndroidNotificationPermissionIntegrationTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `needs a runtime grant until POST_NOTIFICATIONS is granted`() {
        val permission = AndroidNotificationPermission(app)
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertThat(permission.needsRuntimeGrant()).isTrue()

        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertThat(permission.needsRuntimeGrant()).isFalse()
    }
}
