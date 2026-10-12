package com.gb6105.musictimely.receiver

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** 매니페스트 계약 (SRS Android §7.1). */
@RunWith(AndroidJUnit4::class)
class ManifestIntegrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val info = context.packageManager.getPackageInfo(
        context.packageName,
        PackageManager.GET_PERMISSIONS or PackageManager.GET_RECEIVERS,
    )

    @Test
    fun `declares notification permission and none of the forbidden permissions`() {
        val requested = info.requestedPermissions.orEmpty().toList()

        assertThat(requested).contains(Manifest.permission.POST_NOTIFICATIONS)
        assertThat(requested).containsNoneOf(
            "android.permission.SCHEDULE_EXACT_ALARM",
            "android.permission.USE_EXACT_ALARM",
            "android.permission.QUERY_ALL_PACKAGES",
            "android.permission.READ_MEDIA_AUDIO",
            "android.permission.MEDIA_CONTENT_CONTROL",
        )
    }

    @Test
    fun `the media access listener requires the system bind permission`() {
        val services = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SERVICES).services.orEmpty()
        val listener = services.single { it.name == com.gb6105.musictimely.service.MediaAccessListener::class.java.name }

        assertThat(listener.permission).isEqualTo(Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE)
        assertThat(listener.exported).isTrue()
    }

    @Test
    fun `text shares are received by the share activity (FR-A023)`() {
        val resolved = context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_SEND).setType("text/plain").setPackage(context.packageName), 0,
        )

        assertThat(resolved.map { it.activityInfo.name }).containsExactly(com.gb6105.musictimely.ui.share.ShareReceiveActivity::class.java.name)
    }

    @Test
    fun `the session action receiver is registered and not exported`() {
        val receiver = info.receivers.orEmpty().single { it.name == SessionActionReceiver::class.java.name }

        assertThat(receiver.exported).isFalse()
        val resolved = context.packageManager.queryBroadcastReceivers(
            Intent(SessionActionReceiver.ACTION_PAUSE).setPackage(context.packageName), 0,
        )
        assertThat(resolved).isEmpty()
    }
}
